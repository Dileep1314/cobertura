package groovy.US

import static java.util.UUID.randomUUID
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Map
import java.util.concurrent.CompletableFuture
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
import com.metlife.utility.TenantContext
import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.ValidationUtil
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import net.minidev.json.parser.JSONParser

import com.metlife.gssp.configuration.GSSPConfiguration

/**
 * This groovy is used to retrieve the billing
 * dashboard details
 *
 * Call : SPI
 *author: vakul
 */

class GET_DASHBOARD_DETAILS implements Task {
	def static final EVENT_TYPE_GROUP = 'BILL_PROFILE_GROUP'
	def static final EVENT_TYPE_DEDUCTION_SCHEDULE = 'BILLING_DEDUCTION_SCHEDULE_SUMMARY'
	def static final EVENT_TYPE_BILLING_SCHEDULE = 'BILLING_SCHEDULE_LIST'
	def static final EVENT_TYPE_BILLING_HISTORY = 'BILLING_HISTORY_LIST'
	def static final EVENT_TYPE_PAYMENT_HISTORY_DETAILED = 'PAYMENTS_HISTORY_LIST'
	def static final EVENT_TYPE_PAYMENT_HISTORY = 'PAYMENTS_HISTORY_SUMMARY'
	def static final EVENT_TYPE_PREMIUM_DETAILS = 'PREMIUMDETAILS'
	def static final CHANNEL_ID = 'GSSPUI'
	def static final COLLECTION_NAME7 = 'premiumDetails'
	def billMethodMap =[:]
	private static final Logger LOGGER = LoggerFactory.getLogger(GET_DASHBOARD_DETAILS)

	@Override
	Object execute(WorkflowDomain workFlow) {
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def viewSrvVip = workFlow.getEnvPropertyFromContext(BillingConstants.BILL_PROFILE_VIEW_SERVICE_VIP)
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList?.tokenize(BillingConstants.COMMA) , requestHeaders)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]

		def subGroupsDetails = retrieveBillProfileGroupFromSPI(registeredServiceInvoker, spiPrefix,groupNumber,spiMockURI,spiHeadersMap)

		if(subGroupsDetails == null){
			subGroupsDetails = []
		}
		if (subGroupsDetails != null) {
			subGroupsDetails.each({subGroup ->
				saveBillProfileSubGroupsDetailstoDB(entityService, subGroup, tenantId, subGroup.item.accountNumber)
			})
			saveBillProfileDetailstoDB(entityService,subGroupsDetails,tenantId, groupNumber)
		}
		if (subGroupsDetails != null && subGroupsDetails.size() >0) {
			createBillProfileSubGroupData(subGroupsDetails,entityService,registeredServiceInvoker,tenantId,groupNumber,viewSrvVip)
			def subGroupEffectiveDate
			def billingScheduleDueDate
			def billingScheduleAvailableDate
			subGroupsDetails.each({subGroupsDetail ->
				CompletableFuture.supplyAsync({
					->
					def scheduleMap = [:]
					def billingSchedule = [:]
					def billingScheduleDetails
					def deductionScheduleDetails
					def billHistory = [:]
					subGroupEffectiveDate = subGroupsDetails?.item.extension.effectiveDate
					def effectiveDateResponse = setEffectiveDate(subGroupEffectiveDate[0])
					billingScheduleDetails = retrieveBillingScheduleFromSPI(registeredServiceInvoker, spiPrefix, subGroupsDetail.item.accountNumber,spiMockURI, effectiveDateResponse.startDate, effectiveDateResponse.endDate,spiHeadersMap)
					def cal = Calendar.getInstance();
					cal.add(Calendar.YEAR, -1);
					def billFromDate = cal.getTime();
					billFromDate = new SimpleDateFormat(BillingConstants.DATE_FORMAT).format(billFromDate)
					billHistory = retrieveBillSummaryFromSPI(registeredServiceInvoker,billFromDate, spiPrefix, subGroupsDetail.item.accountNumber, spiMockURI, spiHeadersMap)
					def response = setPastStatementFlag(subGroupEffectiveDate, billingScheduleDetails)
					for(res in response){
						if (!res.isEnabled){
							billingScheduleDueDate = res.dueDate
							billingScheduleAvailableDate = res.scheduledSendDate
							break;
						}
					}
					deductionScheduleDetails = retrieveDeductionScheduleFromSPI(registeredServiceInvoker, spiPrefix, subGroupsDetail.item.accountNumber, spiMockURI,effectiveDateResponse.startDate, effectiveDateResponse.endDate, spiHeadersMap)
					if (billHistory != null) {
						saveBillingHistorytoDB(entityService,billHistory,tenantId, subGroupsDetail.item.accountNumber, subGroupEffectiveDate[0])
					}
					if (billingScheduleDetails != null) {
						saveBillingScheduleDetailstoDB(entityService,response,tenantId, subGroupsDetail.item.accountNumber)
					}
					if (deductionScheduleDetails != null) {
						saveDeductionScheduletoDB(entityService,deductionScheduleDetails,tenantId, subGroupsDetail.item.accountNumber)
					}
					scheduleMap << [ 'payrollDeduction' : true ]
					scheduleMap << [ 'groupNumber' : subGroupsDetail.item.accountNumber ]
					billingSchedule << [ 'billingAvailableDate' : billingScheduleAvailableDate]
					billingSchedule << [ 'billingDueDate' : billingScheduleDueDate]
					scheduleMap << [ 'billingSchedule' : billingSchedule ]
					if( deductionScheduleDetails != null){
						scheduleMap = bulidDeductionMap(scheduleMap,deductionScheduleDetails)
					}
					def billProfile = [:]
					billProfile << [ 'billingDeductionSchedule' : response ]
					def eventDataMapBillingScheduleList = buildEventDataBillingScheduleList(scheduleMap, subGroupsDetail.item.accountNumber, tenantId)
					callBillingScheduledListViewService(registeredServiceInvoker, viewSrvVip, eventDataMapBillingScheduleList, tenantId, subGroupsDetail.item.accountNumber)
					def eventDataMapBillingSchedule = buildEventDataBillingSchedule(billProfile, subGroupsDetail.item.accountNumber, tenantId)
					callViewService(registeredServiceInvoker, viewSrvVip, eventDataMapBillingSchedule, tenantId, subGroupsDetail.item.accountNumber)
					def paymentHistoryDetails = retrievePaymentHistoryFromSPI(registeredServiceInvoker, spiPrefix, subGroupsDetail.item.accountNumber,spiMockURI, spiHeadersMap)
					def paymentHistoryDetailed = retrievePaymentHistoryDetailedFromSPI(registeredServiceInvoker, spiPrefix, subGroupsDetail.item.accountNumber,spiMockURI, spiHeadersMap)
					if (paymentHistoryDetailed != null) {
						savePaymentHistoryDetailedtoDB(entityService,paymentHistoryDetailed,tenantId, subGroupsDetail.item.accountNumber)
					}
					def eventDataMapPaymentHistory = buildEventDataPaymentHistory(paymentHistoryDetails, subGroupsDetail.item.accountNumber, tenantId)
					callPaymentHistoryListViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPaymentHistory, tenantId, subGroupsDetail.item.accountNumber)
					def paymentHistoryMap =[:]
					paymentHistoryMap << [ 'billingDeductionSchedule' : paymentHistoryDetailed ]
					paymentHistoryMap << [ 'years':getYears(subGroupEffectiveDate[0])]
					def eventDataMapPaymentHistoryDetailed = buildEventDataPaymentHistoryDetailed(paymentHistoryMap, subGroupsDetail.item.accountNumber, tenantId)
					callPaymentHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPaymentHistoryDetailed, tenantId, subGroupsDetail.item.accountNumber)

					def premiumDetails = retrievePremiumDetailsFromSPI(registeredServiceInvoker, spiPrefix, subGroupsDetail.item.accountNumber, spiMockURI, spiHeadersMap, subGroupsDetail.item.number)
					if(premiumDetails !=null){
						savePremiumDetailstoDB(entityService,premiumDetails,tenantId, subGroupsDetail.item.accountNumber)
					}

					def eventDataMapPremiumDetails = buildEventDataPremiumDetails(premiumDetails, subGroupsDetail.item.accountNumber, tenantId, config)
					def billPremium = eventDataMapPremiumDetails?.resourceName_PREMIUMDETAILS?.billPremiumDetails
					def deductionSchedule =eventDataMapPremiumDetails?.resourceName_PREMIUMDETAILS?.deductionSchedule

					if(billPremium){
						def dataToBeUpdated = buildBillPremiumDataToBeUpdated(entityService, billPremium, subGroupsDetail.item.accountNumber, tenantId)
						saveBillPremiumDetailstoDB(entityService, dataToBeUpdated, tenantId, subGroupsDetail.item.accountNumber,)
					}
					if(deductionSchedule){
						def dataToBeUpdated = buildDeductionDataToBeUpdated(entityService, deductionSchedule, subGroupsDetail.item.accountNumber, tenantId)
						saveDeductionScheduleDetailstoDB(entityService,dataToBeUpdated,tenantId, subGroupsDetail.item.accountNumber)
					}
					if(eventDataMapPremiumDetails) {
						callPremiumDetailsViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPremiumDetails, tenantId, subGroupsDetail.item.accountNumber)
					}
				})
			})
		}
		workFlow.addResponseStatus(HttpStatus.OK)
		workFlow.addResponseBody(new EntityResult([response:'final response'], true))
	}

	def buildBillPremiumDataToBeUpdated(entityService, hydratedDataMap, groupNumber, tenantId) {
		def data = []
		def billDetails = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, groupNumber, data)
		billDetails << [ 'billDetails' : hydratedDataMap]
		billDetails
	}

	def saveBillPremiumDetailstoDB(entityService,dataToBeUpdated,tenantId, groupNumber) {
		try{
			def searchQuery = ['_id':groupNumber]
			TenantContext.setTenantId(tenantId)
			def updatedData = entityService.updateByQuery(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, searchQuery, dataToBeUpdated)
			TenantContext.cleanup()
		}catch(e){
			LOGGER.info('Error saving premiumDetails ' +"${e.message}")
		}
	}

	def buildDeductionDataToBeUpdated(entityService, hydratedDataMap, groupNumber, tenantId) {
		def data = []
		def deductionDetails = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, groupNumber, data)
		deductionDetails << [ 'deductionDetails' : hydratedDataMap]
		deductionDetails
	}

	def saveDeductionScheduleDetailstoDB(entityService,dataToBeUpdated,tenantId, groupNumber) {
		try{
			def searchQuery = ['_id':groupNumber]
			TenantContext.setTenantId(tenantId)
			def updatedData = entityService.updateByQuery(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, searchQuery, dataToBeUpdated)
			TenantContext.cleanup()
		}catch(e){
			LOGGER.info('Error saving premiumDetails ' +"${e.message}")
		}
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
	def callPaymentHistoryListViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, groupNumber){
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/paymentTransactionList"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		response
	}

	protected saveGroupDetailsToDB(entityService,groupDetails,tenantId, groupNumber){
		def groupResponse = [:]
		try{
			groupResponse << [ 'view_GROUP_DETAILS' : groupDetails]
			def searchQuery = ['_id':groupNumber]
			TenantContext.setTenantId(tenantId)
			entityService.updateByQuery(BillingConstants.COLLECTION_NAME_BILL_PROFILE, searchQuery, groupResponse)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving groupDetails ' +"${e.message}")
			throw new GSSPException("20004")
		}
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
	def callPaymentHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, groupNumber){

		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/paymentTransactionSummary"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		LOGGER.info("response from callPaymentHistoryDetailedViewService is:"+response)
		response
	}

	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataPaymentHistory(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_PAYMENT_HISTORY
		eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_PAYMENT_HISTORY, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PAYMENT_HISTORY, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		eventDataMapGroup
	}

	def getLastYear(){
		def year = Calendar.getInstance().get(Calendar.YEAR)
		def mon = Calendar.getInstance().get(Calendar.MONTH)
		def day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
		def lastYear
		if(mon < 10){
			lastYear = "0"+(mon)+"/"+(day+1)+"/"+(year)
		}else{
			lastYear = (mon)+"/"+(day+1)+"/"+(year)
		}
		lastYear
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
		def resName = 'resourceName' + '_' + EVENT_TYPE_PAYMENT_HISTORY_DETAILED
		eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_PAYMENT_HISTORY, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PAYMENT_HISTORY_DETAILED, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		eventDataMapGroup
	}

	protected savePaymentHistoryDetailedtoDB(entityService,billingScheduleDetails,tenantId, groupNumber) {
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_PAYMENT_HISTORY, groupNumber)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status'+'_' + EVENT_TYPE_PAYMENT_HISTORY_DETAILED
			def accountDetailRes = [_id:groupNumber, data: billingScheduleDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_PAYMENT_HISTORY, accountDetailRes)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving paymentHistory ' +"${e.message}")
			throw new GSSPException("20004")
		}
	}

	/*
	 * Used to get bill profile details from SPI
	 */
	def retrievePaymentHistoryDetailedFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI, spiHeadersMap) {
		def uri
		LOGGER.info('inside payment history call 2')
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/payments?q=number==$groupNumber"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/payments"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info('service uri is::'+serviceUri)
		def response
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap) //getTestData('paymentDetails')
			LOGGER.info('paymentHistoryDetailed response from spi data is::' +responseData)
			response = responseData.getBody().items.item //getTestData('paymentDetails').items.item
			LOGGER.info('SPI Response extracted item'+ response)
		} catch (e) {
			LOGGER.error "ERROR while retrieving paymentHistory details from SPI....."+e.toString()
		}
		response
	}

	/*
	 * Used to get bill profile details from SPI
	 */
	def retrievePaymentHistoryFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI, spiHeadersMap) {
		def uri
		LOGGER.info('inside payment spi call 1')
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/payments?q=number==$groupNumber"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/payments"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info('final service uri is::'+serviceUri)
		def response
		def numberOfTransaction
		def paymentTransaction = 0
		def refundTransaction = 0
		try {
			Date start = new Date()

			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap) //getTestData('paymentDetails').items.item

			Date stop = new Date()
			TimeDuration elapseTime = TimeCategory.minus(stop,start)
			LOGGER.info("Time taken to receive response from IIB for PaymentHistory--->" + elapseTime)
			LOGGER.info("Response from IIB for paymentHistory--->" + JsonOutput.toJson(responseData))

			responseData = responseData.getBody()?.items?.item
			response = responseData[0]
			//			numberOfTransaction = responseData.getBody().metadata.count
			//			response << ['numberOfTransaction': numberOfTransaction]
			numberOfTransaction = responseData
			if (numberOfTransaction != null && !numberOfTransaction.isEmpty()) {
				for (def transact  : numberOfTransaction.typeCode) {
					if (transact == 'REFUND') {
						refundTransaction = refundTransaction+1
					} else {
						paymentTransaction = paymentTransaction+1
					}
				}
				response << ['numberOfPaymentTransaction': paymentTransaction]
				response << ['numberOfRefundTransaction': refundTransaction]
			}

		} catch (e) {
			LOGGER.error "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		LOGGER.info(' final responseData payment history is::'+serviceUri)
		response
	}

	/**
	 * Used to save the payment deduction schedule details in deductionSchedule collection in DB
	 *
	 * @param entityService
	 * @param deductionScheduleDetails
	 * @param tenantId
	 * @return
	 */
	protected saveDeductionScheduletoDB(entityService,deductionScheduleDetails,tenantId, groupNumber) {
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_DEDUCTION_SCHEDULE, groupNumber)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status' + '_' + EVENT_TYPE_DEDUCTION_SCHEDULE
			def accountDetailRes = [_id:groupNumber, data: deductionScheduleDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_DEDUCTION_SCHEDULE, accountDetailRes)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving deduction schedule ' +"${e.message}")
			throw new GSSPException("20004")
		}
	}

	/**
	 * Used to save the billing schedule details in billingSchedule collection in DB
	 *
	 * @param entityService
	 * @param billingScheduleDetails
	 * @param tenantId
	 * @return
	 */
	protected saveBillingScheduleDetailstoDB(entityService,billingScheduleDetails,tenantId, groupNumber) {
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_BILLING_SCHEDULE, groupNumber)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status' + '_' + EVENT_TYPE_BILLING_SCHEDULE
			def accountDetailRes = [_id:groupNumber, data: billingScheduleDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_BILLING_SCHEDULE, accountDetailRes)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving billing Schedule ' +"${e.message}")
			throw new GSSPException("20004")
		}
	}

	protected saveBillingHistorytoDB(entityService,billingScheduleDetails,tenantId, groupNumber, effectiveDate) {
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_BILL_HISTORY, groupNumber)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status' + '_' + EVENT_TYPE_BILLING_HISTORY
			def billHistory = [:]
			billHistory << ['billingScheduleDetails' : billingScheduleDetails ]
			billHistory << ['years' :getYears(effectiveDate) ]
			def accountDetailRes = [_id:groupNumber, data: billHistory,  (status):'NA']

			entityService.create(BillingConstants.COLLECTION_NAME_BILL_HISTORY, accountDetailRes)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saveBillingHistorytoDB ' +"${e.message}")
			throw new GSSPException("20004")
		}
	}

	def getYears(date){
		DateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		def currentYear = Calendar.getInstance().get(Calendar.YEAR)
		def year = sdf.parse(date).getAt(Calendar.YEAR)
		def diffYears  = currentYear - year
		def years = []
		for (int i=0;i<=diffYears;i++){
			years.add(year+i)
		}
		years
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
	def callViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, groupNumber){

		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/billingScheduleSummary"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		response
	}

	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataBillingSchedule(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_BILLING_SCHEDULE
		eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_BILLING_SCHEDULE, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_BILLING_SCHEDULE, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		eventDataMapGroup
	}

	def buildEventDataDeductionSchedule(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_DEDUCTION_SCHEDULE
		eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_DEDUCTION_SCHEDULE, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_DEDUCTION_SCHEDULE, tenantId: tenantId,
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
	def callBillingScheduledListViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, groupNumber){
		LOGGER.error("inside callBillingScheduledListViewService.....")
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/billingScheduleList"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		LOGGER.info("inside callBillingScheduledListViewService....."+response)
		response
	}

	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataBillingScheduleList(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_DEDUCTION_SCHEDULE
		eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_BILLING_SCHEDULE, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_DEDUCTION_SCHEDULE, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		eventDataMapGroup
	}

	def callDeductionScheduledListViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, groupNumber){
		LOGGER.info("inside callBillingScheduledListViewService.....")
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/billingScheduleList"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		LOGGER.info("inside callBillingScheduledListViewService....."+response)
		response
	}


	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataDeductionScheduleList(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_DEDUCTION_SCHEDULE
		eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_BILLING_SCHEDULE, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_DEDUCTION_SCHEDULE, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		eventDataMapGroup
	}

	/*
	 * Used to set past statement flag
	 */
	def setPastStatementFlag(effectiveStartDate, billingScheduleDetails){
		try{
			LOGGER.error("inside ")
			def year = Calendar.getInstance().get(Calendar.YEAR)
			def stDate = new Date().parse("MM/dd/yyyy", effectiveStartDate[0])
			use(TimeCategory) {
				billingScheduleDetails.each({billingScheduleDetail ->
					def scheduledSendDate = billingScheduleDetail.scheduledSendDate
					Date date = new Date()
					def currentDate = date.format("MM/dd/yyyy")
					def actualSendDate = billingScheduleDetail.actualSendDate
					if (actualSendDate != null && scheduledSendDate < currentDate){
						LOGGER.error("inside setPastStatementFlag actualSendDate null")
						billingScheduleDetail.isEnabled = true
						LOGGER.error("inside afer setPastStatementFlag actualSendDate null")
					}
					else {
						billingScheduleDetail.isEnabled = false
					}
				})
			}
		}catch(Exception e) {
			LOGGER.error("Exception inside setPastStatementFlag"+e.getMessage())
		}
		billingScheduleDetails
	}

	def retrieveDeductionScheduleFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI,startDate, endDate, spiHeadersMap){
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/deductionSchedules?q=number==$groupNumber"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/deductionSchedules?q=DeductionSchedulePeriodFrom==$startDate;DeductionSchedulePeriodTo==$endDate"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			Date start = new Date()
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			Date stop = new Date()
			TimeDuration elapseTime = TimeCategory.minus(stop,start)
			LOGGER.info("Time taken to receive response from IIB for DeductionSchedule--->" + elapseTime)
			LOGGER.info("Response from IIB for DeductionSchedules--->" + JsonOutput.toJson(responseData))
			response = responseData.getBody()?.items?.item
		} catch (e) {
			LOGGER.error "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		response
	}

	def retrieveBillingScheduleFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI, startDate, endDate, spiHeadersMap){
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billingSchedules?q=number==$groupNumber"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/billingSchedules?q=BillSchedulePeriodFrom==$startDate;BillSchedulePeriodTo==$endDate"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			Date start = new Date()
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			Date stop = new Date()
			TimeDuration elapseTime = TimeCategory.minus(stop,start)
			LOGGER.info("Time taken to receive response from IIB for billingSchedules--->" + elapseTime)
			LOGGER.info("Response from IIB for billingSchedules--->" + JsonOutput.toJson(responseData))
			response = responseData.getBody()?.items?.item
		} catch (e) {
			LOGGER.error "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		response
	}

	/*
	 * Used to get bill profile details from SPI
	 */
	def retrieveBillProfileGroupFromSPI(registeredServiceInvoker, spiPrefix,groupNumber,spiMockURI, spiHeadersMap) {
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billProfiles?q=accountNumber==$groupNumber"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=view==current"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			Date start = new Date()
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			Date stop = new Date()
			TimeDuration elapseTime = TimeCategory.minus(stop,start)
			LOGGER.info("Time taken to receive response from IIB for Current billProfile--->" + elapseTime)
			LOGGER.info("Response from IIB for current bill profile--->" + JsonOutput.toJson(responseData))
			response = responseData.getBody().items
		} catch (Exception e) {
			LOGGER.error "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		response
	}

	def retrieveBillSummaryFromSPI(registeredServiceInvoker,billFromDate, spiPrefix, accountNumber, spiMockURI, spiHeadersMap) {
		def uri
		def todaysDate = new Date().format( 'MM/dd/yyyy' )
		LOGGER.info('todaysDate retrieveBillSummaryFromSPI....' +todaysDate)
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billProfiles?q=number==$accountNumber"
			LOGGER.info('uri retrieveBillSummaryFromSPI if....' +uri)
		} else {
			uri = "${spiPrefix}/groups/$accountNumber/billProfiles?q=billFromDate==$billFromDate;billToDate==$todaysDate;view==history"
			LOGGER.info('uri retrieveBillSummaryFromSPI else....' +uri)
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			Date start = new Date()
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			Date stop = new Date()
			TimeDuration elapseTime = TimeCategory.minus(stop,start)
			LOGGER.info("Time taken to receive response from IIB for HistoryBill--->" + elapseTime)
			LOGGER.info("Response from IIB for history bill--->" + JsonOutput.toJson(responseData))
			response = responseData.getBody()?.items
			LOGGER.info('responseData retrieveBillSummaryFromSPI final....' +responseData)
		} catch (Exception e) {
			LOGGER.info "ERROR WHILE retrieving bill summary from SPI....."+e.toString()
		}
		response
	}

	def callTemplateService(registeredServiceInvoker, templateConfigServiceVip, requestData, tenantId, prodCode,typeVal) {
		LOGGER.error "Invoking template config service"
		def response
		def templateConfigServiceUri = "/v1/tenants/${tenantId}/templates"
		try{
			def requestToTemplate = [type: typeVal, prodCode:prodCode, data :requestData]
			def responseTemplate = registeredServiceInvoker.post(templateConfigServiceVip, templateConfigServiceUri, new HttpEntity(requestToTemplate), Map)
			response = responseTemplate?.getBody().item
		} catch (e) {
			LOGGER.error("Error while invoking template config service",e)
		}
		response
	}

	/**
	 * Used to save the payment preference details in billProfile collection in DB
	 *
	 * @param entityService
	 * @param billProfileDetails
	 * @param tenantId
	 * @return
	 */
	protected saveBillProfileDetailstoDB(entityService,billProfileDetails,tenantId, groupNumber) {
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_BILL_PROFILE, groupNumber)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status' + '_' + EVENT_TYPE_GROUP
			def accountDetailRes = [_id:groupNumber, data: billProfileDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_BILL_PROFILE, accountDetailRes)
			TenantContext.cleanup()
		} catch (e) {
			LOGGER.error('Error saving paymentPreference ' +"${e.message}")
		}
	}

	protected saveBillProfileSubGroupsDetailstoDB(entityService,billProfileDetails,tenantId, groupNumber) {
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, groupNumber)
			LOGGER.error('Inside Bill Profile Sub Group DB')
			def status = 'status' + '_' + EVENT_TYPE_GROUP
			def subGroupDetailRes = [_id:groupNumber, data: billProfileDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, subGroupDetailRes)
			TenantContext.cleanup()
		} catch (e) {
			LOGGER.error('Error saving paymentPreference ' +"${e.message}")
		}
	}

	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataGroup(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]

		def resName = 'resourceName' + '_' + EVENT_TYPE_GROUP
		eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_BILL_PROFILE, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_GROUP, tenantId: tenantId,
			resourceName: (resName), (resName):resourceContents]
		LOGGER.error "buildEventData Event Data " + eventDataMapGroup
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
	def callGroupViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, groupNumber){

		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/clientPaymentsDashboard"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		response
	}

	/*
	 * Used to set set effective date
	 */
	def setEffectiveDate(effectiveStartDate){
		def responseMap = [:]
		try{
			LOGGER.debug("inside setEffectiveDate...")
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy")
			def currentDate = formatter.parse(formatter.format(new Date()))
			def sDate
			def eDate
			use(TimeCategory) {
				sDate = new Date().parse("MM/dd/yyyy", effectiveStartDate)
				eDate =  sDate + 1.year - 1.day
			}
			boolean checkDates = true
			while (checkDates) {
				if (eDate.before(currentDate)) {
					use(TimeCategory) {
						sDate = sDate + 1.year
						eDate = eDate + 1.year
					}
					
				} else {
					checkDates = false
				}
			}
			
			def startDate = formatter.format( sDate );
			def endDate = formatter.format( eDate );
			responseMap <<['startDate':startDate]
			responseMap <<['endDate': endDate]
		}catch(Exception e) {
			LOGGER.error("Exception inside setPastStatementFlag"+e.getMessage())
		}
		responseMap
	}

	def frequencyCode(val) {
		def frequency
		switch (val) {
			case 100:
				frequency = 'Weekly'
				break
			case 101:
				frequency = 'Bi Weekly'
				break
			case 102:
				frequency = 'Monthly'
				break
			case 103:
				frequency = 'Semi Monthly'
				break
			case 104:
				frequency = 'Quarterly'
				break
			case 105:
				frequency = 'Semi Annually'
				break
			case 106:
				frequency = 'Annually'
				break
			default:
				frequency = 'Default'
				break
		}
		frequency
	}
	public def retrievePremiumDetailsFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI, spiHeadersMap, billNumber) {
		def uri
		billNumber = ''+billNumber
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/memberPremiumDetails?q=number==$groupNumber"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/billPremiumDetails?q=billNumber==$billNumber"
			LOGGER.info("PremiumDetails uri is: " + uri)
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info("PremiumDetails final url is: " + serviceUri)
		def response
		try {
			Date start = new Date()

			response = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)

			Date stop = new Date()
			TimeDuration elapseTime = TimeCategory.minus(stop,start)
			LOGGER.info("Time taken to receive response from IIB for PremiumDetails--->" + elapseTime)
			LOGGER.info("Response from IIB for PremiumDetails--->" + JsonOutput.toJson(response))

			def resp = response.getBody()
			LOGGER.info('resp--------------------->: '+resp)
			if(response.getBody() != null) {
				LOGGER.info('premium details response is 1: '+response)
				response = resp
				LOGGER.info('premium details response is: '+response)
			}
		} catch (e) {
			LOGGER.info('response is: '+response)
			LOGGER.info("ERROR while retrieving premium details from SPI....."+e.toString()) //changed to info log as error log is not workinng
		}
		response
	}

	def savePremiumDetailstoDB(entityService,premiumDetails,tenantId, groupNumber) {
		LOGGER.info('Inside savePremiumDetailstoDB ')
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, groupNumber)
			LOGGER.info('Inside Bill Profile DB')
			def status = 'status'+'_'+ EVENT_TYPE_PREMIUM_DETAILS
			def accountDetailRes = [_id:groupNumber, data: premiumDetails, (status):'NA']
			LOGGER.info('premium accountDetailRes------->'+ accountDetailRes)
			entityService.create(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, accountDetailRes)
			TenantContext.cleanup()
		}catch(e){
			LOGGER.info('Error saving premiumDetails ' +"${e.message}")
		}
	}

	def buildEventDataPremiumDetails(resourceContents, groupNumber, tenantId, config){
		LOGGER.info "inside the buildEventDataPremiumDetails ------------>"
		def response = [:] as Map
		def eventDataMapGroup = [:]
		def memberPremium = resourceContents?.memberPremiumDetails?.items
		LOGGER.info "memberPremium --------->"+ memberPremium
		def billPremium = resourceContents?.billPremiumDetails?.items
		LOGGER.info "billPremium --------->"+ billPremium
		def resName = 'resourceName' + '_' + EVENT_TYPE_PREMIUM_DETAILS
		if(!resourceContents.empty) {
			response << ["metadata" : resourceContents.metadata]

			response << ["memberAdjustments" : getAdjPremiumDetails(memberPremium)]
			response << ["memberPremiumDetails" : getPremiumDetails(memberPremium)]
			response << ["coverageDetails" : getCoverageDetails(memberPremium)]

			//deductionDetails
			def finalDeduction = getDeductionDetails(memberPremium)
			LOGGER.info("finalDeduction----"+ finalDeduction)
			Date start = new Date()
			response << ["deductionSchedule": getFinalDeductionDetails(finalDeduction , config) ]
			Date stop = new Date()
			TimeDuration elapseTime = TimeCategory.minus(stop,start)
			LOGGER.info("Time taken for getFinalDeductionDetails--->" + elapseTime)
			LOGGER.info "deductionSchedulet final response Data ------------>" + response

			//product breakdown
			def productBreakdown = getFinalProduct(billPremium , config)
			LOGGER.info "productBreakdown with key and value" + productBreakdown
			response << ["billPremiumDetails" : productBreakdown]

			LOGGER.info "billPremiumDetails fianl response---------->" + response

			if (response.memberAdjustments == '' || response.memberAdjustments == null || response.memberAdjustments == 'null' || response.memberAdjustments == "") {
				response << ["noOfAdj" : 0]
			}else{
				response << ["noOfAdj" : response.memberAdjustments.size]
			}

			response << ["numberOfInsured" : memberPremium.item[0].numberOfInsured]
			response << ["numberOfDependents" : memberPremium.item[0].numberOfDependents]
			eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PREMIUM_DETAILS, tenantId: tenantId,
				resourceName: (resName), (resName):response]
			LOGGER.info "buildEventData Event Data " + eventDataMapGroup
		}
		eventDataMapGroup
	}

	def getFinalProduct(product , config){
		LOGGER.info("Inside getFinalProduct" + product)
		def productName = [:] as Map
		def respList = [] as Set
		product.each({details ->
			HashMap<String,String> respMap = new HashMap<String,String>()
			HashMap<String,String> respDetilMap = new HashMap<String,String>()
			respMap<<['productName':details.item.receivableCode]
			respMap<<['numberOfLives':details.item.numberOfLives]
			respMap<<['totalCoverageAmount':details.item?.totalCoverageAmount?.amount?.toString()]
			respMap<<['memberCount':details.item?.memberCount]
			def volume = details?.item?.volume
			if(volume != 'N/A'){
				int vol = Integer.parseInt(volume)
				String finalVolume = NumberFormat.getIntegerInstance().format(vol);
				respMap<<['volume': finalVolume]
			}else{
				respMap<<['volume':volume]
			}
			respMap<<['totalProductPremium': details?.item?.totalProductPremium?.amount.toString()]

			def reciCode = details.item.receivableCode
			reciCode = getProdName(reciCode, config, 'ref_self_admin_details')
			respMap<<['productName':reciCode]

			respDetilMap.put('item', respMap)
			respList.add(respDetilMap)
			//HashMap respProdMap = new HashMap()
		})
		def counts
		def productCount
		def response = respList
		LOGGER.info "response----->"+ response
		counts = response.size()
		productCount = counts.toString()

		productName << ['productDetails': respList]
		LOGGER.info"productDetails----------->"+ productName
		productName << ['productCount': productCount]

		return productName

	}

	def getCoverageDetails(memberPremiumDetails){
		def coverageDetails = []
		memberPremiumDetails.each ({memberPremiumDetail ->
			def memberCertificateList = []
			def memberCertificates = memberPremiumDetail.item.memberCertificates
			def totalPremium = 0.0
			def adjAmt = 0.0
			def premiumAmt = 0.0
			memberCertificates.each({ memberCertificate ->
				def memberCertificateResp = [:]
				def receivableCode = memberCertificate?.receivableCode
				memberCertificateResp << [ "productName" :  memberCertificate.product.nameCode]
				memberCertificateResp << [ "receivableCode" :  receivableCode]
				memberCertificateResp << [ "typeCode" :  memberCertificate.product.typeCode]
				memberCertificateResp << [ "billMonth" :  memberCertificate?.billMonth]
				memberCertificateResp << [ 'adjustmentReasonCode' : memberCertificate.adjustmentReasonCode ]
				premiumAmt = (memberCertificate.employerPremiumAmount.amount).toFloat() + (memberCertificate.employeePremiumAmount.amount).toFloat()
				adjAmt = (memberCertificate.adjustmentAmounts[0].employerAdjustmentAmount.amount).toFloat() + (memberCertificate.adjustmentAmounts[0].employeeAdjustmentAmount.amount).toFloat()
				LOGGER.info "memberCertificateResp------------------>" + memberCertificateResp
				memberCertificateResp << [ 'premiumAmt' : premiumAmt +  adjAmt]

				totalPremium += memberCertificateResp.premiumAmt
				LOGGER.info("Coverage MemberCertificate Details: "+"memberCertificateResp: "+memberCertificateResp+"adjAmt"+adjAmt)
				if (premiumAmt == '0.0' ||  premiumAmt == 0.0 || premiumAmt.equals("0.0")) {
					LOGGER.info("Variance if block...")
					memberCertificateResp << [ 'variance' :  '']
				}
				else {
					LOGGER.info("Variance else block....")
					def variance
					if (memberCertificateResp.premiumAmt > 0) {
						variance = (adjAmt/memberCertificateResp.premiumAmt)*100
					} else {
						variance = (adjAmt/-(memberCertificateResp.premiumAmt))*100
					}

					memberCertificateResp << [ 'variance' :  variance]
				}
				//Set teir for non-tier based coverages based on receivable code else pass tier code as is
				def tierCode = DownloadUtil.setTierBasedOnReceivableCode(receivableCode, memberCertificate?.tierCode)
				memberCertificateResp << [ "tier": tierCode]

				def volume = memberCertificate?.volume
				if(volume != 'N/A'){
					int vol = Integer.parseInt(volume)
					String finalVolume = NumberFormat.getIntegerInstance().format(vol);
					memberCertificateResp<<['volume': finalVolume]
				}else{
					memberCertificateResp<<['volume':volume]
				}

				memberCertificateList.add(memberCertificateResp)
			})
			def adjResp = [:]
			adjResp << ["memberCertificates" : memberCertificateList]
			adjResp << ["firstName" : memberPremiumDetail.item.memberName.firstName]
			adjResp << ["lastName" : memberPremiumDetail.item.memberName.lastName]
			adjResp << ["number" : memberPremiumDetail.item.number]
			adjResp << ["totalPremium" : totalPremium]
			adjResp << ["insuredDate": memberPremiumDetail.item.effectiveDate]
			coverageDetails.add(adjResp)
		})
		coverageDetails
	}

	def getDeductionDetails(memberPremiumDetail){
		LOGGER.info("getDeductionDetails------" + memberPremiumDetail)
		for(details in memberPremiumDetail ){
			int count = 0
			def deductList = [] as List
			def finalDeductList = [] as List
			def deductMap = [:] as Map
			def detailResp =[:]
			HashMap<String,String> respDetilMap = new HashMap<String,String>()

			for(certificate in details?.item?.memberCertificates ){
				boolean deducFlag = certificate?.isDeductionCandidate
				def deductionDetails = certificate?.deductionSchedule
				if(deducFlag == true){
					for(int i=0; i<deductionDetails.size(); i++){
						deductMap<< ["deductionDate" : deductionDetails[i]?.deductionDate]
						deductMap<< ["deductionAmount" :  deductionDetails[i]?.deductionAmount.amount.toString()]
						deductMap<< ["coverage" : certificate.receivableCode]
						String resp= new JsonBuilder(deductMap).toPrettyString()
						def edpmRequestBodyMap = new JsonSlurper().parseText(resp)
						//deductList[i] = edpmRequestBodyMap
						deductList.add(edpmRequestBodyMap)
					}
				}else{
					LOGGER.info( "deducFlag is false")
				}
			}
			if(deductList.size() != 0 ) {
				details?.item <<["deductionSchedule" : deductList]
			}
		}
		memberPremiumDetail
	}

	def getFinalDeductionDetails(finalDeduction, config){
		def coverageDetails = [] as List
		def deductionScheduleDetails = [] as List

		def deductionScheduleResp = [:]
		def finalResp= [:]
		def deductionDate
		def deductionAmount
		def respList = [] as List

		finalDeduction.each({ details->

			def deductionDetails = details?.item?.deductionSchedule
			if (deductionDetails!= null){
				def detailResp =[:]
				def memberName = [:]
				def deductionResp = [:]
				def finalList = []
				for(int i=0; i<deductionDetails.size(); i++){
					deductionResp<< ["deductionDate" : deductionDetails[i]?.deductionDate]
					deductionResp<< ["deductionAmount" :  deductionDetails[i]?.deductionAmount]
					def coverage = deductionDetails[i]?.coverage
					deductionResp<< ["coverage" : getProdName(coverage, config, 'ref_self_admin_details')]
					String resp= new JsonBuilder(deductionResp).toPrettyString()
					def edpmRequestBodyMap = new JsonSlurper().parseText(resp)
					finalList[i] = edpmRequestBodyMap
				}
				detailResp<< ["memberId" : details.item.number]
				memberName<< ["firstName": details.item.memberName.firstName]
				memberName<< ["lastName": details.item.memberName.lastName]
				detailResp<< ["memberName": memberName]
				detailResp.putAt('deductionSchedule', finalList)
				deductionScheduleDetails.add(detailResp)
				finalResp<< ["items":deductionScheduleDetails ]
				LOGGER.info( "deduc finalResp---" + finalResp)
			}
		})
		finalResp
	}

	def getAdjPremiumDetails(memberPremiumDetails){
		def premiumDetails = []
		memberPremiumDetails.each ({memberPremiumDetail ->
			def memberCertificates = memberPremiumDetail.item.memberCertificates
			def isAdjustmentReasonCode = false
			def memberCertificateList = []
			def totalPremium = 0.0
			def adjAmt = 0.0
			def premiumAmt = 0.0
			memberCertificates.each({ memberCertificate ->
				def memberCertificateResp = [:]
				if(memberCertificate.adjustmentReasonCode != null && !memberCertificate.adjustmentReasonCode.isEmpty() && memberCertificate.adjustmentReasonCode != 'MISSING_DATA_CONFIG'){
					isAdjustmentReasonCode = true
					def receivableCode = memberCertificate?.receivableCode
					memberCertificateResp << [ 'adjustmentDate' : memberCertificate.adjustmentDate ]
					memberCertificateResp << [ 'productName' :  receivableCode]
					memberCertificateResp << [ 'adjustmentReasonCode' : memberCertificate.adjustmentReasonCode ]
					memberCertificateResp << ['department' : memberCertificate?.department]
					premiumAmt = (memberCertificate.employerPremiumAmount.amount).toFloat() + (memberCertificate.employeePremiumAmount.amount).toFloat()
					adjAmt = (memberCertificate.adjustmentAmounts[0].employerAdjustmentAmount.amount).toFloat() + (memberCertificate.adjustmentAmounts[0].employeeAdjustmentAmount.amount).toFloat()

					memberCertificateResp << [ 'premiumAmt' : premiumAmt+adjAmt ]

					totalPremium += memberCertificateResp.premiumAmt
					LOGGER.info("Adjustment MemberCertificate Details: "+"memberCertificateResp: "+memberCertificateResp+"adjAmt"+adjAmt)
					if (premiumAmt == '0.0' ||  premiumAmt == 0.0 || premiumAmt.equals("0.0")) {
						LOGGER.info("Variance if block...")
						memberCertificateResp << [ 'variance' : '']
					}
					else {
						LOGGER.info("Variance else block....")
						if (premiumAmt > 0) {
							memberCertificateResp << [ 'variance' :  (adjAmt/premiumAmt)*100]
						} else {
							memberCertificateResp << [ 'variance' :  (adjAmt/-(premiumAmt))*100]
						}

					}
					//Set teir for non-tier based coverages based on receivable code else pass tier code as is
					def tierCode = DownloadUtil.setTierBasedOnReceivableCode(receivableCode, memberCertificate?.tierCode)
					memberCertificateResp << [ "tier": tierCode]

					def volume = memberCertificate?.volume
					if(volume != 'N/A'){
						int vol = Integer.parseInt(volume)
						String finalVolume = NumberFormat.getIntegerInstance().format(vol);
						memberCertificateResp<<['volume': finalVolume]
					}else{
						memberCertificateResp<<['volume':volume]
					}

					memberCertificateList.add(memberCertificateResp)
				}
			})
			if(isAdjustmentReasonCode){
				def adjResp = [:]
				adjResp << ["memberCertificates" : memberCertificateList]
				adjResp << ["firstName" : memberPremiumDetail.item.memberName.firstName]
				adjResp << ["lastName" : memberPremiumDetail.item.memberName.lastName]
				adjResp << ["number" : memberPremiumDetail.item.number]
				adjResp << ["cobra" : memberPremiumDetail?.item?.COBRAIndicator]
				adjResp << ["class" : memberPremiumDetail.item.class.number]
				adjResp << ["classDescription" : memberPremiumDetail.item.class.name]
				adjResp << ["totalPremium" : totalPremium]
				premiumDetails.add(adjResp)
			}
		})
		premiumDetails
	}

	def getPremiumDetails(memberPremiumDetails){
		def premiumDetails = []
		memberPremiumDetails.each ({memberPremiumDetail ->
			def memberCertificates = memberPremiumDetail.item.memberCertificates
			def memberCertificateList = []
			def totalPremium = 0.0
			def premiumAmt = 0.0
			def variance = 0.0
			def adjAmt = 0.0
			def totalPrevPrmAmt = 0.0
			def totalAdjAmt = 0.0
			memberCertificates.each({ memberCertificate ->
				def memberCertificateResp = [:]
				premiumAmt = (memberCertificate.employerPremiumAmount.amount).toFloat() + (memberCertificate.employeePremiumAmount.amount).toFloat()
				adjAmt = (memberCertificate.adjustmentAmounts[0].employerAdjustmentAmount.amount).toFloat() + (memberCertificate.adjustmentAmounts[0].employeeAdjustmentAmount.amount).toFloat()
				memberCertificateResp << [ 'premiumAmt' : premiumAmt+adjAmt ]
				totalPrevPrmAmt += premiumAmt
				totalAdjAmt += adjAmt
				totalPremium += premiumAmt + adjAmt
				LOGGER.info("Premium details member certificate PremiumAmmount: "+premiumAmt+" AdjustmentAmmount: "+adjAmt)

				memberCertificateResp << ['productName' :  memberCertificate.receivableCode]

				def volume = memberCertificate.volume
				if(volume != 'N/A'){
					int vol = Integer.parseInt(volume)
					String finalVolume = NumberFormat.getIntegerInstance().format(vol);
					memberCertificateResp<<['volume': finalVolume]
				}else{
					memberCertificateResp<<['volume':volume]
				}
				LOGGER.debug("Before calling setTierBasedOnReceivableCode method... tierCode is "+memberCertificate?.tierCode)
				//Set teir for non-tier based coverages based on receivable code else pass tier code as is
				def tierCode = DownloadUtil.setTierBasedOnReceivableCode(memberCertificate?.receivableCode, memberCertificate?.tierCode)
				LOGGER.debug("After calling setTierBasedOnReceivableCode method... tierCode is: "+tierCode)
				memberCertificateResp << ["tierCode" : tierCode]
				memberCertificateResp << ["billMonth" : memberCertificate.billMonth]
				memberCertificateResp << ['department' : memberCertificate?.department]
				memberCertificateList.add(memberCertificateResp)
			})
			def adjResp = [:]
			LOGGER.info("Premium details member certificate total adjustment amount: "+totalAdjAmt+" total previous amount : "+totalPrevPrmAmt)
			if (totalPrevPrmAmt == '0.0' || totalPrevPrmAmt == 0.0 || totalPrevPrmAmt.equals("0.0")) {
				LOGGER.info("Variance if block...")
				adjResp << ["variance" : '']
			}
			else {
				LOGGER.info("Variance else block...")
				if (totalPrevPrmAmt > 0) {
					variance = (totalAdjAmt/totalPrevPrmAmt)*100
				} else {
					variance = (totalAdjAmt/-(totalPrevPrmAmt))*100
				}

				adjResp << ["variance" : variance]
			}
			adjResp << ["memberCertificates" : memberCertificateList]
			adjResp << ["firstName" : memberPremiumDetail.item.memberName.firstName]
			adjResp << ["lastName" : memberPremiumDetail.item.memberName.lastName]
			adjResp << ["number" : memberPremiumDetail.item.number]
			adjResp << ["class" : memberPremiumDetail.item.class.number]
			adjResp << ["cobra" : memberPremiumDetail.item.COBRAIndicator]
			adjResp << ["classDescription" : memberPremiumDetail.item.class.name]
			adjResp << ["totalPremium" : totalPremium]
			adjResp << ["seeCoverage":"See Coverage"]
			premiumDetails.add(adjResp)
		})
		premiumDetails
	}

	def callPremiumDetailsViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPremiumDetails, tenantId, groupNumber){
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/memberPremiumDetails"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(eventDataMapPremiumDetails), Map.class)
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

	def getBillingMethod(code , config, tenantId, configurationId){
		code = code.toString()
		if(billMethodMap[code]){
			billMethodMap[code]
		}else {
			def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
			billMethodMap = statusMapping?.data
			billMethodMap[code]
		}
	}

	def deleteById(entityService,collectionName, groupNumber) {
		try {
			entityService.deleteById(collectionName,groupNumber)
		}catch(Exception e) {
			LOGGER.info("No data to delete: "+e.getMessage())
		}
	}
	def filterDiductionDates(deductionDates) {

		def filteredDeductionDates = []

		try {
			def currentDate = new Date()
			def currentYear = new SimpleDateFormat("yyyy").format(currentDate)
			def currentMonth = new SimpleDateFormat("MM").format(currentDate)
			if(deductionDates) {
				for(def deductionDate : deductionDates) {
					def deductionDay = Date.parse("MM/dd/yyyy",deductionDate)
					def deductionYear = new SimpleDateFormat("yyyy").format(deductionDay)
					def deductionMonth = new SimpleDateFormat("MM").format(deductionDay)
					if(currentYear == deductionYear) {
						if(currentMonth == deductionMonth) {
							filteredDeductionDates.add(deductionDate)
						}
					}
				}
			}
		}catch(Exception e) {
			LOGGER.error("Error occoured during date conversion.."+e.getMessage())
		}
		filteredDeductionDates
	}

	def bulidDeductionMap(scheduleMap,deductionScheduleDetails){
		def classList = []
		def deductionSchedule = []
		def count = 0 as Integer
		deductionScheduleDetails.each({deductionScheduleDetail ->
			def classMap =[:]
			classMap << [ 'key' : count.toString() ]
			classMap << [ 'value' : deductionScheduleDetail.class.code ]
			classList[count] = classMap
			def deductionMap = [:]
			deductionMap << [ 'class' : deductionScheduleDetail.class.code ]
			deductionMap << [ 'deductionDate' : filterDiductionDates(deductionScheduleDetail.deductionDates) ]
			deductionMap << [ 'frequency' : frequencyCode(Integer.parseInt(""+deductionScheduleDetail.frequencyMode)) ]
			deductionMap << [ 'frequencyParameter' : deductionScheduleDetail.frequencyParameter ]
			if(deductionScheduleDetail.deductionHolidayRuleCode == 200) {
				deductionMap << [ 'deductionHolidayRuleCode' : 'Pay Before Holidays' ]
			}else {
				deductionMap << [ 'deductionHolidayRuleCode' : 'Pay After Holidays' ]
			}
			deductionMap << [ 'exclusionList' : deductionScheduleDetail.exclusionPeriod ]
			deductionSchedule[count] = deductionMap
			count = count+1
		})

		scheduleMap << [ 'classArray' : classList ]
		scheduleMap << [ 'deductionSchedule' : deductionSchedule ]
	}

	def createBillProfileSubGroupData(subGroupsDetails,entityService,registeredServiceInvoker,tenantId,groupNumber,viewSrvVip) {

		def groupDueAmount = 0.0
		def groupBillAmount = 0.0
		def groupOutstandingAmount = 0.0
		def groupFeesAmount = 0.0
		def groupPaymentReceivedAmount = 0.0
		def groupLastBillAmount = 0.0
		def groupSuspenseAmount = 0.0
		def groupCreditsWriteOffsAmount = 0.0
		def groupFeeOtherAdjustment = 0.0
		def subGroupDueAmount = 0.0
		def subGroupsAdditionalDueAmount = 0.0
		def groupDueDate
		def subGroupList = []
		def entityName
		def dueAmountExcel = 0.0
		def outstandingAmountExcel = 0.0
		subGroupsDetails.each({subGroupsDetail ->
			if(groupNumber.equalsIgnoreCase(subGroupsDetail.item.accountNumber))
				entityName = subGroupsDetail.item.extension.entityName

			groupBillAmount = groupBillAmount +(subGroupsDetail.item.billAmount.amount).toFloat()
			groupOutstandingAmount = groupOutstandingAmount + (subGroupsDetail.item.extension.outstandingAmount.amount).toFloat()
			groupPaymentReceivedAmount = groupPaymentReceivedAmount +(subGroupsDetail.item.extension.paymentReceivedAmount.amount).toFloat()
			groupLastBillAmount = groupLastBillAmount + (subGroupsDetail.item.extension.lastBillAmount.amount).toFloat()
			groupSuspenseAmount = groupSuspenseAmount+(subGroupsDetail.item.extension.suspenseAmount.amount).toFloat()
			groupCreditsWriteOffsAmount = groupCreditsWriteOffsAmount+(subGroupsDetail.item.extension.otherAdjustmentsAmount.amount).toFloat()
			subGroupDueAmount = ((subGroupsDetail.item.billAmount.amount).toFloat() + (subGroupsDetail.item.extension.outstandingAmount.amount).toFloat() -(subGroupsDetail.item.extension.paymentReceivedAmount.amount).toFloat())
			subGroupsDetail << ['subGroupDueAmount': subGroupDueAmount.toString()]
			subGroupList.add(subGroupsDetail.item.accountNumber)
			subGroupsAdditionalDueAmount = subGroupsAdditionalDueAmount +(subGroupsDetail.item.dueAmount.amount).toFloat()
			groupDueDate = subGroupsDetail?.item?.dueDate
			dueAmountExcel = dueAmountExcel +(subGroupsDetail?.item?.extension?.dueAmountExcel?.amount).toFloat()
			outstandingAmountExcel = outstandingAmountExcel +(subGroupsDetail?.item?.extension?.outstandingAmountExcel?.amount).toFloat()
		})
		def detailMap = [:]
		groupFeeOtherAdjustment = groupFeesAmount - groupSuspenseAmount + groupCreditsWriteOffsAmount
		groupDueAmount = subGroupsAdditionalDueAmount
		groupFeeOtherAdjustment = new DecimalFormat("#.00").format(groupFeeOtherAdjustment)
		groupDueAmount = new DecimalFormat("#.00").format(groupDueAmount)
		groupBillAmount = new DecimalFormat("#.00").format(groupBillAmount)
		groupOutstandingAmount = new DecimalFormat("#.00").format(groupOutstandingAmount)
		groupPaymentReceivedAmount = new DecimalFormat("#.00").format(groupPaymentReceivedAmount)
		groupLastBillAmount = new DecimalFormat("#.00").format(groupLastBillAmount)
		groupSuspenseAmount = new DecimalFormat("#.00").format(groupSuspenseAmount)
		groupCreditsWriteOffsAmount = new DecimalFormat("#.00").format(groupCreditsWriteOffsAmount)
		dueAmountExcel = new DecimalFormat("#.00").format(dueAmountExcel)
		outstandingAmountExcel = new DecimalFormat("#.00").format(outstandingAmountExcel)

		detailMap << [ 'dueAmount' : groupDueAmount.toString() ]
		detailMap << [ 'billAmount' : groupBillAmount.toString() ]
		detailMap << [ 'dueDate' : groupDueDate ]
		detailMap << [ 'groupNumber' : groupNumber ]
		detailMap << [ 'entityName' : entityName ]
		detailMap << [ 'feesAmount' : groupFeeOtherAdjustment.toString() ]
		detailMap << [ 'lastpayment': groupLastBillAmount.toString() ]
		detailMap << [ 'outstandingAmount': groupOutstandingAmount.toString() ]
		detailMap << [ 'paymentReceived': groupPaymentReceivedAmount.toString() ]
		detailMap << [ 'billFromDate': (subGroupsDetails[0].item.extension.billFromDate).toString() ]
		detailMap << [ 'billToDate': (subGroupsDetails[0].item.extension.billToDate).toString() ]
		detailMap << [ 'dueAmountExcel': dueAmountExcel.toString()]
		LOGGER.info("detailMap after adding dueAmountExcel feild :"+ detailMap)
		detailMap << [ 'outstandingAmountExcel': outstandingAmountExcel.toString()]
		LOGGER.info("detailMap after adding outstandingAmountExcel feild :"+ detailMap)
		subGroupsDetails.each({subGroupDetail ->

			def subTotalAmount = subGroupDetail.item.billAmount.amount
			def calPercentage = (subTotalAmount.toFloat()/detailMap.billAmount.toFloat())*100
			def dataCeild = Math.round(calPercentage).toInteger()
			subGroupDetail << ['percentage': dataCeild.toString()]
		})
		def billProfileMap =[:]
		def groupMap = detailMap
		groupMap << ['suspenseAmount': groupSuspenseAmount.toString()]
		groupMap << ['otherAdjustmentsAmount' : groupFeeOtherAdjustment.toString()]
		groupMap << ['billNumber' : subGroupsDetails[0].item.number]
		billProfileMap << [ 'groupDetails' : detailMap ]
		billProfileMap << [ 'subGroupDetails' :subGroupsDetails ]
		saveGroupDetailsToDB(entityService, groupMap, tenantId, groupNumber)
		def eventDataMapGroup = buildEventDataGroup(billProfileMap, groupNumber, tenantId)
		callGroupViewService(registeredServiceInvoker, viewSrvVip, eventDataMapGroup, tenantId, groupNumber)
	}

	private Map<String, Object> getTestData(String fileName) {
		JSONParser parser = new JSONParser();
		Map<String, Object> jsonObj = null;
		String workingDir = System.getProperty("user.dir");
		Object obj = parser.parse(new FileReader(workingDir+"/src/test/data/"+fileName));
		jsonObj = (HashMap<String, Object>) obj;
		return jsonObj;
	}

	def getProdName(code, config, configurationId){
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		def stateMethodMap=[:]
		stateMethodMap = statusMapping?.data
		stateMethodMap[code.toString()]
	}
}
