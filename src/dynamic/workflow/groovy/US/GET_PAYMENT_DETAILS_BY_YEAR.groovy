package groovy.US

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
 * @author Purushotham
 *
 */
class GET_PAYMENT_DETAILS_BY_YEAR implements Task {

	def static final EVENT_TYPE_PAYMENT_HISTORY = 'PAYMENTS_HISTORY_LIST'
	def static final CHANNEL_ID = 'GSSPUI'
	Logger logger = LoggerFactory.getLogger(GET_PAYMENT_DETAILS_BY_YEAR.class)

	@Override
	Object execute(WorkflowDomain workFlow) {
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]
		def userType = getUserType(groupNumber,entityService,tenantId)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def viewSrvVip = workFlow.getEnvPropertyFromContext(BillingConstants.BILL_PROFILE_VIEW_SERVICE_VIP)
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
		def requestParamsMap = workFlow.getRequestParams()
		def paymentHistoryDetailed = retrievePaymentHistoryDetailedFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,requestParamsMap, userType)
		if (paymentHistoryDetailed  == null) {
			throw new GSSPException("20007")
		}
		def paymentHistoryMap =[:]
		paymentHistoryMap << [ 'billingDeductionSchedule' : paymentHistoryDetailed ]
		def eventDataMapPaymentHistoryDetailed = buildEventDataPaymentHistoryDetailed(paymentHistoryMap, groupNumber, tenantId)
		def hydratedData = callPaymentHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPaymentHistoryDetailed, tenantId, groupNumber)
		def clientsList=[]
		def response = [:]
		def pageValue
		def pageSize
		def totalResult
		if (hydratedData != null) {
			clientsList = hydratedData.getBody().balanceSummary.detailedClientData.items
			totalResult = clientsList.size()
			if (requestParamsMap.get('q')!=null) {
				requestParamsMap?.get('q')?.tokenize(';').each{queryParam ->
					def (key, value) = queryParam.tokenize( '==' )
					if (key != null && key == "offset") {
						pageValue = value
					}
					if (key != null && key == "limit") {
						pageSize = value
					}
				}
			}
		}
		clientsList = getPage(clientsList,pageValue.toInteger(),pageSize.toInteger())
		response << ['paymentHistory':clientsList]
		response << ['years':[]]
		response << ['offset':pageValue.toString()]
		response << ['limit':pageSize.toString()]
		response << ['totalSize':totalResult.toString()]
		workFlow.addResponseBody(new EntityResult([paymentDetails:response], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	def getPage(clientsList, page, pageSize) {
		clientsList.subList(page, Math.min(page + pageSize, clientsList.size()))
	}

	def retrievePaymentHistoryDetailedFromSPI(registeredServiceInvoker, spiPrefix, groupNumber, requestParamsMap, userType) {
		def queryMap = ""
		if (requestParamsMap.get('q')!=null) {
			requestParamsMap?.get('q')?.tokenize(';').each{queryParam ->
				def (key, value) = queryParam.tokenize( '==' )
				if (key != null && key == "year") {
					queryMap = "paidDate>=01-01-$value;paidDate<=31-12-$value"
				}
			}
		}
		def uri
		if(queryMap.length() > 0) {
			queryMap = queryMap.substring(0,queryMap.length()-1)
			if(userType.equals("EMPLOYEE")){
				logger.info("calling the spi for EMPLOYEE payment history details...")
				uri="${spiPrefix}/payments?q=accountNumber==$groupNumber;${queryMap}"
			}else{
				logger.info("calling the spi for payment history details ...")
				uri="${spiPrefix}/groups/$groupNumber/payments?${queryMap}"
			}
		} else {
			throw new GSSPException("30004")
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:])
		} catch (e) {
			logger.info "ERROR while retrieving premium details from SPI....."+e.toString()
		}
		response.getBody().items.item
	}

	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataPaymentHistoryDetailed(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_PAYMENT_HISTORY
		eventDataMapGroup << [groupNumber:groupNumber, viewType:'', accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PAYMENT_HISTORY, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		eventDataMapGroup
	}

	/**
	 * Used to call the view updater service to trigger events to create views
	 *
	 * @param registeredServiceInvoker
	 * @param viewUpdaterSrvVip
	 * @param eventDataMap
	 * @param tenantId
	 *
	 * @return
	 */
	def callPaymentHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, paymentDetails, tenantId, groupNumber){
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/payments"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(paymentDetails), Map)
		response
	}

	def getUserType(groupNumber,entityService,tenantId){
		def response
		def userType
		def data = []
		try{
			userType = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_PROFILEAME, groupNumber, data)
			response = userType.userType
		}catch(Exception e){
			logger.error("Unable to get user type: "+e.getMessage())
			response = ''
		}
		response
	}
}