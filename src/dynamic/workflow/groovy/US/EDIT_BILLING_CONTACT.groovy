package groovy.US

import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.taskflow.Task
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException
import static java.util.UUID.randomUUID
import com.metlife.service.entity.EntityService

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil

/**
 * This groovy is used to update the billing
 * contact details
 *
 * Call : SPI
 *
 * @author Pramit
 */

class EDIT_BILLING_CONTACT implements Task {

	private static final Logger LOGGER = LoggerFactory.getLogger(EDIT_BILLING_CONTACT)
	@Override
	Object execute(WorkflowDomain workFlow) {
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList.tokenize(BillingConstants.COMMA) , requestHeaders)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def  groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]
		def  tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
		def requestBody=workFlow.getRequestBody()
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def userType = getUserType(groupNumber,entityService,tenantId)
		def response = editBillingContact(requestBody , registeredServiceInvoker, userType, groupNumber, spiPrefix,spiMockURI, spiHeadersMap, config)
		if(response== null) {
			throw new GSSPException("20008")
		}
        workFlow.addResponseBody(new EntityResult([clientDetails:response], true))
		workFlow.addResponseStatus(HttpStatus.OK)
		
		
	}

	def getUserType(groupNumber,entityService,tenantId){
		def response
		def userType
		def data = []
		try{
			userType = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_PROFILE, groupNumber, data)
			response = userType.userType
		}catch(Exception e){
			response = ''
			LOGGER.error("Unable to get user type:"+e.getMessage())
		}
		response
	}
	
	def editBillingContact(requestBody , registeredServiceInvoker,userType, groupNumber, spiPrefix,spiMockURI, spiHeadersMap, config) {
		def billContactResp
		try {
			def response
			def uri
			if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
				uri= "${spiPrefix}/billingContacts/$groupNumber"
			} else {
				if(userType.equals("EMPLOYEE")) {
					uri = "${spiPrefix}/groups/employees/$groupNumber/billingContact"
				}
				else{
					uri = "${spiPrefix}/groups/$groupNumber/billingContact"
				}
			}

			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			def serviceUri = uriBuilder.build(false).toString()
			if(requestBody !=null){	
				def stateCode = requestBody.address.stateCode	
				def stateName = getStateMethod(stateCode, config, 'ref_stateCode')
				LOGGER.info("stateCode after conversion: "+stateName)
				requestBody.address.putAt('stateCode', stateName)
			}
			LOGGER.info("requestBody in edit billing.."+ requestBody)
			HttpEntity<String> request = registeredServiceInvoker.createRequest(requestBody, spiHeadersMap)
			LOGGER.info("request in edit billing.."+ request)
			response = registeredServiceInvoker.putViaSPIWithResponse(serviceUri, request, [:])
            LOGGER.info("IIB response of edit billing.."+ response)
			billContactResp = response?.getBody()?.item
			LOGGER.info("BillContactResponse: "+billContactResp)
		} catch (Exception e) {
			LOGGER.error("Unable to edit bill contact:"+e.getMessage())
		}
		billContactResp
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
	
	
	def getStateMethod(code, config, configurationId){
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		def stateMethodMap=[:]
		stateMethodMap = statusMapping?.data
		stateMethodMap[code.toString()]
	}
}

