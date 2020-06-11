package groovy.US

import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.RetrieveScanStatus
import groovy.UTILS.ValidationUtil
import groovy.json.JsonSlurper

import static java.util.UUID.randomUUID

import com.metlife.gssp.taskflow.Task
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.service.entity.EntityService
import com.metlife.service.entity.GSSPEntityService

import org.springframework.http.HttpStatus
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import org.springframework.web.util.UriComponentsBuilder


import java.text.SimpleDateFormat
import java.util.List
import java.util.Map
import net.minidev.json.parser.JSONParser
import org.springframework.data.mongodb.core.query.Criteria
import com.metlife.domain.model.EntityResult
import com.metlife.gssp.common.excel.ExcelGeneration
import com.metlife.gssp.common.excel.impl.ExcelGenerationImpl
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException

class DOWNLOAD_SELF_ADMIN implements Task{

	private static final Logger LOGGER = LoggerFactory.getLogger(DOWNLOAD_SELF_ADMIN)

	@Override
	public Object execute(WorkflowDomain workFlow) {
		def entityService= workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def requestBody=workFlow.getRequestBody()

		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList?.tokenize(BillingConstants.COMMA) , requestHeaders)

		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestBody[BillingConstants.GROUP_NUMBER]

		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)

		def response = [:] as Map

		def billNumber = requestBody['billNumber']
		def billFromDate = requestBody['fromDate']
		def billToDate = requestBody['toDate']
		def view = requestBody['view']
		def selfAdminBillProfileResponse

		if(view != null && view != 'null') {
			if(view == 'current') {
				selfAdminBillProfileResponse = getSelfAdminDetails(entityService, groupNumber,billNumber)
			}else if(view == 'history' && billNumber != null && billNumber != 'null') {
				selfAdminBillProfileResponse = getSelfAdminDetailsFromSPI(registeredServiceInvoker, spiPrefix, view, groupNumber, billNumber, spiHeadersMap, billFromDate, billToDate)
			}else if(view == 'deduction' && billFromDate != null && billToDate != null) {
				//retrieve bill number using from, to and send date from mongoDB
				billNumber = getBillDetailsFromDB(entityService, groupNumber, billFromDate, billToDate)
				if (billNumber != null) {
					selfAdminBillProfileResponse = getSelfAdminDetailsFromSPI(registeredServiceInvoker, spiPrefix, view, groupNumber, billNumber, spiHeadersMap)
				}else {
					throw new GSSPException("20017")
				}
			} else {
				throw new GSSPException("20016")
			}
		}

		if(selfAdminBillProfileResponse==null) {
			throw new GSSPException("20004")
		}
		response << ['selfAdminBillProfiles' : selfAdminBillProfileResponse]
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
		def chartName = workFlow.getEnvPropertyFromContext('chartName')
		def activityListExcel = DownloadUtil.selfAdminForExcelOutput(response, config , chartName, groupNumber, tenantId)
		// Generate excel
		ExcelGeneration excelGeneration = workFlow.getBeanFromContext('excelGenerationImpl', ExcelGenerationImpl.class)
		byte[] xlsBytes =  excelGeneration.generateExcel(activityListExcel, "selfAdmin-xls.xls")
		def excelFilename = DownloadUtil.createExcelFile(excelOutputFileName, xlsBytes)
		file = new File(excelFilename)
		bytesBase64 = file.getBytes().encodeBase64().toString()
		DownloadUtil.antiVirusSecurityScanCode(workFlow, bytesBase64)
		fileData = ['content' : bytesBase64]
		fileData.encodingType = BillingConstants.BASE64
		fileData.contentLength = bytesBase64.length()
		fileData.formatCode = BillingConstants.XLS
		fileData.name = BillingConstants.SELF_ADMIN_BILL_FILE_NAME
		responseArray.add(fileData)
		workFlow.addResponseBody(new EntityResult([Details:responseMap], true))
		workFlow.addResponseStatus(HttpStatus.OK)

	}

	def getSelfAdminDetails(entityService, account, billNumber) {
		def response
		Criteria inCriteria = Criteria.where("_id").is(account)
		inCriteria.and("data.item.accountNumber").is(account)
		if (billNumber!=null) {
			inCriteria.and("data.item.number").is(billNumber)
		}
		try {
			response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, inCriteria).data.item
		} catch (e) {
			LOGGER.error('Error in  getPremiumDetails' +"${e.message}")
		}
		response[0]
	}

	/**
	 * calling SPI to get history of bill profile
	 * @param registeredServiceInvoker
	 * @param spiPrefix
	 * @param spiMockURI
	 * @param groupNumber
	 * @param billNumber
	 * @param spiHeadersMap
	 * @return
	 */
	def getSelfAdminDetailsFromSPI(registeredServiceInvoker, spiPrefix, view, groupNumber,  billNumber, spiHeadersMap) {

		try {
			def response
			def uri
			uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=billNumber==$billNumber;view==history";
			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			def serviceUri = uriBuilder.build(false).toString()
			LOGGER.info("Formed serviceURI is: "+serviceUri)
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:],spiHeadersMap)
			LOGGER.info("Selfadmin SPI response: "+response)
			if (response != null) {
				LOGGER.info("response body: "+response.getBody().items)
				response =  response.getBody()?.items[0]?.item
			}

			return response

		} catch (Exception e) {
			LOGGER.error 'Exception in getting data from SPI for selfAdmin billProfile ' + e.toString()
			throw new GSSPException("30006")
		}

	}

	def getBillDetailsFromDB(entityService, grpNum, frmDate,  toDate) {
		def response
		def billNmbr
		def actualSendDate
		try {
			Criteria inCriteria = Criteria.where("_id").is(grpNum)
			response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_HISTORY, inCriteria).data.billingScheduleDetails[0]
			LOGGER.info("getBillDetailsFromDB response--------------------"+ response)
		} catch (e) {
			LOGGER.error('Error in  getBillDetailsFromDB' +"${e.message}")
		}
		if(response) {
			LOGGER.info("before comparing dates the response is--------------------> "+ response)
			for (def item : response) {
				item = item?.item
				LOGGER.info("item information: "+item)
				if (item != null && !item.isEmpty()) {
					def fdt = item?.extension?.billFromDate
					def tdt = item?.extension?.billToDate
					if ( fdt == frmDate && tdt == toDate) {
						def currBillNmbr = item?.number
						def senddt  = item?.billDate
						if (actualSendDate == null) {
							actualSendDate = senddt
							billNmbr = currBillNmbr
						} else if (actualSendDate < senddt) {
							actualSendDate = senddt
							billNmbr = currBillNmbr
						}
												
					}

				}

			}

		}
		billNmbr
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


