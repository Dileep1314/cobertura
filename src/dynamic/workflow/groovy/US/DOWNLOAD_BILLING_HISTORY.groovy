package groovy.US

import java.text.SimpleDateFormat
import java.util.Map
import net.minidev.json.parser.JSONParser
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.common.excel.ExcelGeneration
import com.metlife.gssp.common.excel.impl.ExcelGenerationImpl
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import static java.util.UUID.randomUUID

import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.ValidationUtil

class DOWNLOAD_BILLING_HISTORY implements Task{
	private static final LOGGER = LoggerFactory.getLogger(DOWNLOAD_BILLING_HISTORY)
	@Override
	Object execute(WorkflowDomain workFlow) {
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]
		boolean isEmployee = false
		def employeId
		if (groupNumber == null){
			employeId = requestPathParamsMap[BillingConstants.ID]
			isEmployee = true
			groupNumber = employeId
		}
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
		
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID)]
		def spiHeadersMap = getRequiredHeaders(headersList?.tokenize(BillingConstants.COMMA) , requestHeaders)
		def year, billToDate,billNumber,billFromDate
		def requestParamsMap = workFlow.getRequestParams()
		def requestBody = workFlow.getRequestBody()
		if (requestParamsMap.get('q')!=null) {
			requestParamsMap?.get('q')?.tokenize(';').each{queryParam ->
				def (key, value) = queryParam.tokenize( '==' )
				if (key != null && key == "year") {
					year = value
				}
			}
		}
		def response
		def fromDate
		def toDate
		billNumber = requestBody['billNumber']
		billFromDate = requestBody['billFromDate']
		billToDate = requestBody['billToDate']
		
		if(billNumber != null){
			fromDate = billFromDate
			toDate = billToDate
		}else if (null != year && !year.equals("\'\'") && !'default'.equals(year)) {
			fromDate = "01/01/$year"
			toDate = "12/31/$year"
		}
		else{
			SimpleDateFormat formatter = new SimpleDateFormat(BillingConstants.DATE_FORMAT);
			Calendar cal = Calendar.getInstance()
			Date toDayDate = cal.getTime()
			cal.add(Calendar.YEAR, -1)
			cal.add(Calendar.DAY_OF_MONTH, 1)
			Date oneYearBackDate = cal.getTime()
			fromDate = formatter.format(oneYearBackDate)
			toDate = formatter.format(toDayDate)
		}
		
		response = retrieveBillSummaryFromSPI(registeredServiceInvoker, spiPrefix, groupNumber, fromDate, toDate,spiHeadersMap,isEmployee)
		
		def file, bytesBase64, fileData
		def responseArray = [] as Set
		def responseMap = ['files' : responseArray]
		def excelOutputPath = workFlow.getEnvPropertyFromContext('templateNewExcelPath') + 'output/'
		File excelFile=new File(excelOutputPath)
		String[] myExcelFiles
		if(excelFile.isDirectory()){
			myExcelFiles = excelFile.list()
			for(files in myExcelFiles) {
				File myFile = new File(excelFile, files)
				myFile.delete()
			}
		}
		String timeStmp = new SimpleDateFormat("yyyyMMddhhmm").format(new Date())
		String excelOutputFileName = "$excelOutputPath$timeStmp"
		def chartName = workFlow.getEnvPropertyFromContext(BillingConstants.CHART_NAME)
		def activityListExcel = DownloadUtil.prepareDataForExcelOutput(response, excelOutputFileName, chartName, year,billNumber, billFromDate, billToDate)
		// Generate excel
		ExcelGeneration excelGeneration = workFlow.getBeanFromContext('excelGenerationImpl', ExcelGenerationImpl.class)
		byte[] xlsBytes =  excelGeneration.generateExcel(activityListExcel, "billingHistory-xls.xls")
		def excelFilename = DownloadUtil.createExcelFile(excelOutputFileName, xlsBytes)
		file = new File(excelFilename)
		bytesBase64 = file.getBytes().encodeBase64().toString()
		fileData = ['content' : bytesBase64]
		fileData.encodingType = BillingConstants.BASE64
		fileData.contentLength = bytesBase64.length()
		fileData.formatCode = BillingConstants.XLS
		fileData.name = BillingConstants.BILL_HISTORY_FILE_NAME
		responseArray.add(fileData)
		workFlow.addResponseBody(new EntityResult([Details:responseMap], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	/**
	 * Used to fetch billing History details from SPI
	 *
	 * @param registeredServiceInvoker
	 * @param spiPrefix
	 * @param groupNumber
	 * @param fromDate
	 * @param toDate
	 * @return billing History Details
	 */
	def retrieveBillSummaryFromSPI(registeredServiceInvoker, spiPrefix, groupNumber, fromDate, toDate,spiHeadersMap,isEmployee) {
		def uri
		if(isEmployee){
			 uri = "${spiPrefix}/groups/employees/$groupNumber/billProfiles?q=billFromDate==$fromDate;billToDate==$toDate;view==history"
		}
		else{
			 uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=billFromDate==$fromDate;billToDate==$toDate;view==history"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		def responseData
		try {
			 responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:],spiHeadersMap)
			response = responseData.getBody().items
		} catch (e) {
			LOGGER.error "ERROR while retrieving billing history details from SPI....."+e.toString()
		}
		response
	}

	
	def getRequiredHeaders(List headersList, Map headerMap) {
		headerMap<<[(BillingConstants.X_GSSP_TRACE_ID):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {
				spiHeaders << [(header): [headerMap[header]]]
			}
		}
		spiHeaders
	}
	private Map<String, Object> getTestData(String fileName) {
		JSONParser parser = new JSONParser();
		Map<String, Object> jsonObj = null;
		String workingDir = System.getProperty("user.dir");
		Object obj = parser.parse(new FileReader(workingDir +
				"/src/test/data/"+fileName));
		jsonObj = (HashMap<String, Object>) obj;
		return jsonObj;
	}
}

