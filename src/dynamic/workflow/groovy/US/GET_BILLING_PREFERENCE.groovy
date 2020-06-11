package groovy.US

import static java.util.UUID.randomUUID

import java.util.concurrent.TimeUnit

import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import com.metlife.service.entity.EntityService

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil


/**
 * This groovy is used to retrieve the billing
 * Preference details
 *
 * Call : SPI
 * author: vakul
 *
 */

class GET_BILLING_PREFERENCE implements Task {
	private static final Logger LOGGER = LoggerFactory.getLogger(GET_BILLING_PREFERENCE)
	def static final BILLING_PREFERENCE = 'BILLING_PREFERENCE'
	@Override
	Object execute(WorkflowDomain workFlow) {
		def RT_1 = System.nanoTime()
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
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
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]

		boolean isEmployee = false
		if(groupNumber == null){
			def employeId = requestPathParamsMap[BillingConstants.ID]
			LOGGER.info "employeeID....." + employeId
			isEmployee = true
			groupNumber = employeId;
		}

		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		def RT_2 = System.nanoTime()
		validation.validateUser(workFlow, validationList)
		def RT_3 = System.nanoTime()
		LOGGER.info("Time taken to validate user is: "+TimeUnit.NANOSECONDS.toMillis(RT_3-RT_2)+" ms")
		LOGGER.info("groupNumber :"+groupNumber)
		def contactResponse = retrievePreferenceFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI, spiHeadersMap,isEmployee)
		LOGGER.info('contactResponse from SPI' +contactResponse)
		if (contactResponse == null) {
			throw new GSSPException("20006")
		}
		def templateConfigServiceVip = workFlow.getEnvPropertyFromContext('billProfileService.templateConfigServiceVip')
		LOGGER.info('templateConfigServiceVip.........' +templateConfigServiceVip)
		def hydratedResponse = callTemplateService(registeredServiceInvoker, templateConfigServiceVip, contactResponse, tenantId)?.en_US
		LOGGER.info "hydratedResponse....." + hydratedResponse
		def billPreference = [:]
		def groupNumberDetail =  hydratedResponse.billPreferenceDetails[0]
		def formatDetail =  hydratedResponse.billPreferenceDetails[1]
		def deliveryMethodDetail =  hydratedResponse.billPreferenceDetails[2]
		def billingFrequencyDetail =  hydratedResponse.billPreferenceDetails[3]
		def billBasisDetail =  hydratedResponse.billPreferenceDetails[4]
		def billMethodCodeDetails = hydratedResponse.billPreferenceDetails[5]
		def dueDayDetail =  hydratedResponse.billPreferenceDetails[6]
		def leadDaysDetail =  hydratedResponse.billPreferenceDetails[7]

		billPreference << ['groupNumber': groupNumberDetail.groupNumber]
		billPreference << ['format': formatDetail.format]
		billPreference << ["deliveryMethod": deliveryMethodDetail.deliveryMethod]
		billPreference << ["billingFrequency": billingFrequencyDetail.billingFrequency]
		billPreference << ["billBasis": billBasisDetail.billBasis]
		billPreference << ["billMethodCode": billMethodCodeDetails.billMethodCode]
		billPreference << ["dueDay": dueDayDetail.dueDay]
		billPreference << ["leadDays": leadDaysDetail.leadDays]
		if(isEmployee){
			def paymentMethod =  hydratedResponse.billPreferenceDetails[8]
			LOGGER.info "paymentMethod....." + paymentMethod
			billPreference << ["paymentMethod": paymentMethod?.paymentMethod]
			LOGGER.info "billPreferencePaymnet....." + billPreference
		}
		LOGGER.info "billPreference....." + billPreference
		def RT_6 = System.nanoTime()
		LOGGER.info("Total time taken by GET_BILLING_PREFERENCE API is: "+TimeUnit.NANOSECONDS.toMillis(RT_6-RT_1)+" ms")
		workFlow.addResponseBody(new EntityResult([editPreference:billPreference], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	def retrievePreferenceFromSPI(RegisteredServiceInvoker registeredServiceInvoker,spiPrefix, groupNumber,spiMockURI, spiHeadersMap,isEmployee) {
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billingPreferences/$groupNumber"
			LOGGER.info "uri....." + uri
		} else {
			if(isEmployee) {
				uri = "${spiPrefix}/groups/employees/$groupNumber/billingPreference"
				LOGGER.info "employee spi uri....." + uri
			}
			else{
				uri = "${spiPrefix}/groups/$groupNumber/billingPreference"
			}
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info "serviceUri....." + serviceUri
		def response
		try {
			def RT_4 = System.nanoTime()
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			def RT_5 = System.nanoTime()
			LOGGER.info("Time taken to receive response from IIB for billPreference API is:"+TimeUnit.NANOSECONDS.toMillis(RT_5-RT_4)+" ms")
			LOGGER.info "responseeeee....." + response
			response = response.getBody().item
			LOGGER.info "billPreference response....." + response
		} catch (Exception e) {
			LOGGER.error 'ERROR WHILE retrieving billing preference from SPI.....' + e.toString()
		}
		response
	}


	def callTemplateService(registeredServiceInvoker, templateConfigServiceVip, dataToBeHydrated, tenantId) {
		def requestToTemplate = [:]
		def templateConfigServiceUri = "/v1/tenants/${tenantId}/templates"
		def responseTemplate
		requestToTemplate = [ type : BILLING_PREFERENCE , prodCode : 'PROD_TYPE_BILLING' , data : dataToBeHydrated ]
		try {
			def RT11 = System.nanoTime();
			responseTemplate = registeredServiceInvoker.post(templateConfigServiceVip, templateConfigServiceUri, new HttpEntity(requestToTemplate), Map)?.getBody()
			def RT12 = System.nanoTime();
			LOGGER.info("Time taken to call templateService for billPreference API is: "+TimeUnit.NANOSECONDS.toMillis(RT12-RT11)+" ms")
			LOGGER.info "responseTemplate" + responseTemplate
		} catch (e) {
			LOGGER.info('Error while invoking template config service', e)
		}
		responseTemplate
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
}

