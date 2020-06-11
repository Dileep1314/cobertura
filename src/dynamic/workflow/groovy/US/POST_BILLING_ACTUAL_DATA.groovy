package groovy.US


import org.springframework.data.mongodb.core.query.Criteria

/**
 * This groovy is used to post the actual bill data
 *
 */

import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.repo.GSSPRepository
import com.metlife.gssp.taskflow.Task
import com.metlife.service.entity.EntityService
import com.metlife.service.entity.GSSPEntityService
import com.metlife.utility.TenantContext

import groovy.UTILS.BillingConstants

import static java.util.UUID.randomUUID

/**
 *
 * @author panchanan
 *
 */

class POST_BILLING_ACTUAL_DATA implements Task{
	private static final Logger LOGGER = LoggerFactory.getLogger(POST_BILLING_ACTUAL_DATA)
	private static billMethodMap = [:]
	@Override
	Object execute(WorkflowDomain workFlow) {
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def viewSrvVip = workFlow.getEnvPropertyFromContext(BillingConstants.BILL_PROFILE_VIEW_SERVICE_VIP)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList.tokenize(BillingConstants.COMMA) , requestHeaders)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def requestBodyMap = workFlow.getRequestBody()
		boolean isFailure = false
		def actualBillData
		actualBillData = requestBodyMap
		if (actualBillData == null) {
			throw new GSSPException("30002")
		}
		def groupNumber = requestBodyMap['accountNumber']
		def billNumber = requestBodyMap['number']
		try{
			actualBillData.extension << ['billMethodCode' : getBillingMethod(actualBillData?.extension.billMethodCode,config,'ref_billing_form_data')]
			actualBillData.extension << ['billStatusTypeCode' : getBillingMethod(actualBillData?.extension.billStatusTypeCode,config,'ref_billing_form_data')]
			actualBillData << ['billMode' : getBillingMethod(actualBillData?.billMode,config,'ref_billing_form_data')]
			actualBillData.extension << ['submissionStatusTypeCode' : getBillingMethod(actualBillData?.extension.submissionStatusTypeCode,config,'ref_billing_form_data')]

			for(def productDetails : actualBillData.extension.productDetails) {
				def typeCode = productDetails?.product?.typeCode
				// Removing receivableCode and its name from product object and persisting in DB
				def rCodeName = productDetails.product.receivableCodeName
				productDetails.product.remove('receivableCode')
				productDetails.product.remove('receivableCodeName')
				
				for(def receivable : productDetails.receivable){
					def tierCode = receivable?.tierCode
					if (tierCode != null && !tierCode.isEmpty()) {
						receivable << ['tierCode' : getBillingMethod(tierCode,config,'ref_self_admin_details')]
					}
					receivable << ['receivableCode' : getBillingMethod(rCodeName,config,'ref_self_admin_details')]
				}
				if(typeCode != null && !typeCode.isEmpty()) {
					productDetails.product << ['typeCode': getBillingMethod(typeCode,config,'ref_self_admin_details')]
				}
			}
			def statusCode
			def spiResponse = updateBillingActualsToSPI(actualBillData, registeredServiceInvoker, groupNumber, spiPrefix, spiMockURI,spiHeadersMap)
			spiResponse = spiResponse as ResponseEntity
			LOGGER.info("SPI response outside method :"+spiResponse)
			LOGGER.info("Status code :"+spiResponse.getStatusCode())
			statusCode = spiResponse.statusCodeValue
			LOGGER.info("statusCode :"+statusCode)
			if(statusCode == 200){
				LOGGER.info("integer if block")
				LOGGER.info("Before extracting failure response from SPI body...")
				def failure = spiResponse.getBody()?.errors
				LOGGER.info("Failre response..."+failure)
			if (failure!= null) {
				actualBillData = failure[0]
				isFailure = true
			}
			else{
				actualBillData = spiResponse.getBody()
			}
			}  
		}catch(Exception e){
			actualBillData << ['isSubmittedActuals' : 'false']
			LOGGER.error('Error forming actual data: '+e.getMessage())
		}
		if (!isFailure) {
			deleteDraftInfoByID(entityService, "selfAdminDraft", billNumber)
			
		} 
		workFlow.addResponseBody(new EntityResult([actualBillData:actualBillData], true))
		workFlow.addResponseStatus(HttpStatus.OK)
		
	}



	def updateBillingActualsToSPI(requestBody , registeredServiceInvoker, groupNumber, spiPrefix, spiMockURI, spiHeadersMap) {
		def response
		try {
			def uri
			if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
				uri= "${spiPrefix}/selfAdminBillProfile"
			} else {
				uri = "${spiPrefix}/groups/$groupNumber/billProfiles"
			}
			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			def serviceUri = uriBuilder.build(false).toString()
			HttpEntity<String> request = registeredServiceInvoker.createRequest(requestBody,spiHeadersMap)
			response=registeredServiceInvoker.postViaSPI(serviceUri, request, Map.class)
			LOGGER.info 'Response :'+response;
		} catch (Exception e) {
			LOGGER.error 'Exception in posting SPI for selfAdminBillProfile ' + e.toString()
			throw new GSSPException("20013")
		}
		response
	}

	def getBillingMethod(code , config, configurationId){
		code = code.toString()
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		billMethodMap = statusMapping?.data
		billMethodMap[code]
	}
	def getRequiredHeaders(List headersList, Map headerMap) {
		headerMap<<[(BillingConstants.X_GSSP_TRACE_ID):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {

				spiHeaders << [(header): headerMap[header]]
			}
		}
		spiHeaders
	}

	void deleteDraftInfoByID(GSSPEntityService entityService,collectionName,billNumber) {
		try {
			entityService.deleteById(collectionName, billNumber)
			LOGGER.info("deleteDraftInfoByID: record with _id "+billNumber+" deleted successfully...")
			TenantContext.cleanup()
		} catch (Exception exp) {
			LOGGER.error("deleteDraftInfoByID: record could not be deleted "+exp.getMessage())
		}
	}
}

