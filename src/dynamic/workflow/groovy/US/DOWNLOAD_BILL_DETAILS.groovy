package groovy.US

import com.metlife.gssp.taskflow.Task
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.service.entity.EntityService

import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.ValidationUtil
import java.text.SimpleDateFormat
import org.springframework.data.mongodb.core.query.Criteria
import com.metlife.domain.model.EntityResult
import org.springframework.http.HttpStatus
import com.metlife.gssp.common.excel.ExcelGeneration
import com.metlife.gssp.common.excel.impl.ExcelGenerationImpl
import com.metlife.gssp.exception.GSSPException


class DOWNLOAD_BILL_DETAILS implements Task{
	private static final Logger LOGGER = LoggerFactory.getLogger(DOWNLOAD_BILL_DETAILS)

	@Override
	
	Object execute(WorkflowDomain workFlow) {
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def id = requestPathParamsMap[BillingConstants.ID]
		def response = [:] as Map
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(id)
		validation.validateUser(workFlow, validationList)
		
		def premiumResponse = getPremiumDetails(entityService, id)
		LOGGER.info(premiumResponse + "premiumResponse")
		def groupDetails = getGroupDetails(entityService, id)
		LOGGER.info(groupDetails + "groupDetails")
		if(premiumResponse == null || groupDetails==null ) {
			throw new GSSPException("20011")
		}
		response << ['premiumDetails' : premiumResponse[0]]
		response << ['groupDetails' : groupDetails ]
		def file, bytesBase64, fileData
		def responseArray = [] as Set
		def responseMap = ['files' : responseArray]
		def excelOutputPath = workFlow.getEnvPropertyFromContext('templateNewExcelPath') + 'output/premium'
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
		def activityListExcel = DownloadUtil.premiumDataForExcelOutput(response, excelOutputFileName, chartName, id)
		// Generate excel
		ExcelGeneration excelGeneration = workFlow.getBeanFromContext('excelGenerationImpl', ExcelGenerationImpl.class)
		byte[] xlsBytes =  excelGeneration.generateExcel(activityListExcel, "premiumDetails-xls.xls")
		def excelFilename = DownloadUtil.createExcelFile(excelOutputFileName, xlsBytes)
		file = new File(excelFilename)
		bytesBase64 = file.getBytes().encodeBase64().toString()
		fileData = ['content' : bytesBase64]
		fileData.encodingType = 'BASE64'
		fileData.contentLength = bytesBase64.length()
		fileData.formatCode = 'XLS'
		fileData.name = 'activity_list.xls'
		responseArray.add(fileData)
		workFlow.addResponseBody(new EntityResult([Details:responseMap], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	/**
	 * Used to retrieve the subGroup views from subGroupDetails collection in DB
	 *
	 * @param entityService
	 * @param groupNumber
	 *
	 * @return billingHostory
	 */
	def getPremiumDetails(entityService, id) {
		def response
		Criteria inCriteria = Criteria.where("_id").is(id)
		try {
			response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, inCriteria).data.item.memberCertificates
		} catch (e) {
			LOGGER.error('Error in  getPremiumDetails' +"${e.message}")
		}
		response[0]
	}

	def getGroupDetails(entityService, id){
		try {
			Criteria inCriteria = Criteria.where("_id").is(id)
			def response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_PROFILE, inCriteria).view_GROUP_DETAILS
			response[0]
		} catch (e) {
			LOGGER.error('Error in  getGroupDetails' +"${e.message}")
		}
	}
}
