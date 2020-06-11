package groovy.US

import static java.util.UUID.randomUUID

import java.text.SimpleDateFormat

import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder
import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.common.excel.ExcelGeneration
import com.metlife.gssp.common.excel.impl.ExcelGenerationImpl
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task

import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.RetrieveScanStatus
import groovy.UTILS.ValidationUtil

class DOWNLOAD_SELF_ADMIN_VOIDED_ACTUALS implements Task{

	private static final Logger LOGGER = LoggerFactory.getLogger(DOWNLOAD_SELF_ADMIN_VOIDED_ACTUALS)

	@Override
	public Object execute(WorkflowDomain workFlow) {
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def requestBody=workFlow.getRequestBody()

		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestBody[BillingConstants.GROUP_NUMBER]
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
		def response = [:] as Map
		def billNumber = requestBody['billNumber']
		def versionNumber = requestBody['version']

		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList.tokenize(BillingConstants.COMMA) , requestHeaders)
		def selfAdminBillProfileResponse = getSelfAdminDetailsSPI(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber, tenantId, billNumber, versionNumber, spiHeadersMap)
		if(selfAdminBillProfileResponse==null) {
			throw new GSSPException("20012")
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
		def activityListExcel = DownloadUtil.selfAdminForVoidedActuals(response, config , chartName, groupNumber, tenantId)
		// Generate excel
		ExcelGeneration excelGeneration = workFlow.getBeanFromContext('excelGenerationImpl', ExcelGenerationImpl.class)
		byte[] xlsBytes =  excelGeneration.generateExcel(activityListExcel, "selfAdmin-voided-xls.xls")
		def excelFilename = DownloadUtil.createExcelFile(excelOutputFileName, xlsBytes)
		file = new File(excelFilename)
		bytesBase64 = file.getBytes().encodeBase64().toString()
		DownloadUtil.antiVirusSecurityScanCode(workFlow, bytesBase64)
		fileData = ['content' : bytesBase64]
		fileData.encodingType = BillingConstants.BASE64
		fileData.contentLength = bytesBase64.length()
		fileData.formatCode = BillingConstants.XLS
		fileData.name = BillingConstants.SELF_ADMIN_VOIDED_FILE_NAME
		responseArray.add(fileData)
		workFlow.addResponseBody(new EntityResult([Details:responseMap], true))
		workFlow.addResponseStatus(HttpStatus.OK)

	}

	def getSelfAdminDetailsSPI(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber, tenantId, billNumber, versionNumber, spiHeadersMap) {
		
		try {
			def response
			def uri
			if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
				uri= "${spiPrefix}/selfAdminBillProfile"
			} else {
				uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=billNumber==$billNumber;versionNumber==$versionNumber"
			}
			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			def serviceUri = uriBuilder.build(false).toString()
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:],spiHeadersMap)
			if (response != null) {
				LOGGER.info("download:Selfadmin SPI response for specific version: "+response)
				return response.getBody().item 
			}
			response
		} catch (Exception e) {
			LOGGER.error 'Exception in getting SPI for selfAdminVoidedActual ' + e.toString()
		}
	}

	def getRequiredHeaders(List headersList, Map headerMap) {
		LOGGER.info "Configuring spi hearder"
		headerMap<<[(BillingConstants.X_GSSP_TRACE_ID):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {
				spiHeaders << [(header): [headerMap[header]]]
			}
		}
		spiHeaders
	}
}
