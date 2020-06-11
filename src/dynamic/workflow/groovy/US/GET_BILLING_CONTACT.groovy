package groovy.US

import com.metlife.domain.model.WorkflowDomain
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder
import com.metlife.domain.model.EntityResult
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import static java.util.UUID.randomUUID

import java.util.concurrent.TimeUnit

import com.metlife.service.entity.EntityService
import com.metlife.gssp.configuration.GSSPConfiguration

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil

/**
 * This groovy is used to retrieve the billing
 * contact details
 *
 * Call : SPI
 *
 * @author Vakul
 */

class GET_BILLING_CONTACT implements Task {
	private static final Logger LOGGER = LoggerFactory.getLogger(GET_BILLING_CONTACT)

	@Override
	Object execute(WorkflowDomain workFlow) {
		def RT_1 = System.nanoTime()
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList.tokenize(BillingConstants.COMMA) , requestHeaders)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]
		
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		def RT_2 = System.nanoTime()
		validation.validateUser(workFlow, validationList)
		def RT_3 = System.nanoTime()
		LOGGER.info("Time taken to validate user is: "+TimeUnit.NANOSECONDS.toMillis(RT_3-RT_2)+" ms")
		
		//def userType = getUserType(groupNumber,entityService,tenantId)
		def contactResponse = retrieveContactFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI, spiHeadersMap)
		LOGGER.info "contactResponse from SPI" + contactResponse
		
		if(contactResponse.size() != 0) {
			def stateCode = contactResponse.address.stateCode
			def stateName = getStateMethod(stateCode, config, 'ref_stateCode')
			contactResponse.address.putAt('stateCode', stateName)
		}	
		def RT_6 = System.nanoTime()
		LOGGER.info("Total time taken by billingContact API is: "+TimeUnit.NANOSECONDS.toMillis(RT_6-RT_1)+" ms")	
		workFlow.addResponseBody(new EntityResult([clientDetails:contactResponse], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	def getUserType(groupNumber,entityService,tenantId){
		def response
		def userType
		def data = []
		try{
			userType = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_PROFILE, groupNumber, data)
			LOGGER.info "userType from Profile collection..." + userType
			response = userType.userType
			LOGGER.info "Responce from Profile collection..." + response
		}catch(Exception e){
			LOGGER.info "ERROR WHILE retrieving user profile....." + e.toString()
			response = ''
		}
		response
	}
	/*
	 * Used to get billing contact details from SPI
	 */

	def retrieveContactFromSPI(registeredServiceInvoker, spiPrefix, groupNumber, spiMockURI, spiHeadersMap) {
		def uri
		def response = [:]
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billingContacts/$groupNumber"
		} else {
				uri = "${spiPrefix}/groups/$groupNumber/billingContact"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		
		try {
			def RT_4 = System.nanoTime()
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			def RT_5 = System.nanoTime()
			LOGGER.info("Time taken to receive response from IIB for billContact API is: "+TimeUnit.NANOSECONDS.toMillis(RT_5-RT_4)+" ms")
			response = response.getBody().item
			LOGGER.info "Responce from SPI" + response
		} catch (e) {
			LOGGER.info "ERROR WHILE retrieving billing contact detail from SPI....." + e.printStackTrace()
			response = [:]
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
		LOGGER.info "spiHeaders" + spiHeaders
		spiHeaders
	}
	
	
	def getStateMethod(code, config, configurationId){
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		def stateMethodMap=[:]
		stateMethodMap = statusMapping?.data
		stateMethodMap[code.toString()]
	}
}

