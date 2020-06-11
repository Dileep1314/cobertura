package groovy.US

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

import static java.util.UUID.randomUUID

import java.text.DateFormat
import java.text.SimpleDateFormat

import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.ValidationUtil
import groovy.time.TimeCategory
import net.minidev.json.parser.JSONParser

/**
 * This groovy is used to retrieve the employee billing
 * dashboard details
 *
 * Call : SPI
 *
 *@author MonikaRawat
 *
 */

class GET_EMPLOYEE_DASHBOARD_DETAILS implements Task {
	def static final EVENT_TYPE_GROUP = 'BILL_PROFILE_GROUP'
	def static final EVENT_TYPE_DEDUCTION_SCHEDULE = 'BILLING_DEDUCTION_SCHEDULE_SUMMARY'
	def static final EVENT_TYPE_BILLING_SCHEDULE = 'BILLING_SCHEDULE_LIST'
	def static final EVENT_TYPE_BILLING_HISTORY = 'BILLING_HISTORY_LIST'
	def static final EVENT_TYPE_PAYMENT_HISTORY_DETAILED = 'PAYMENTS_HISTORY_LIST'
	def static final EVENT_TYPE_PAYMENT_HISTORY = 'PAYMENTS_HISTORY_SUMMARY'
	def static final EVENT_TYPE_PREMIUM_DETAILS = 'PREMIUMDETAILS'
	def static final EVENT_TYPE_BILLING_CONTACT = 'BILLING_CONTACT'
	def static final CHANNEL_ID = 'GSSPUI'
	def static final COLLECTION_NAME7 = 'premiumDetails'


	private static final Logger LOGGER = LoggerFactory.getLogger(GET_EMPLOYEE_DASHBOARD_DETAILS)

	@Override
	public Object execute(WorkflowDomain workFlow) {

		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def viewSrvVip = workFlow.getEnvPropertyFromContext(BillingConstants.BILL_PROFILE_VIEW_SERVICE_VIP)
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
		def employeeId = requestPathParamsMap[BillingConstants.ID]

		def employeesDetails =retrieveEmployeesBillProfileDetailsFromSPI(registeredServiceInvoker, spiPrefix,employeeId,spiMockURI,spiHeadersMap)
		LOGGER.info("employeesDetails information..."+employeesDetails)
		if(employeesDetails!= null){
			employeesDetails.item << ['accountNumber' : employeeId]
			saveBillProfileDetailstoDB(entityService,employeesDetails,tenantId, employeeId)
		}

		def employeeEffectiveDate
		def billingScheduleDetails
		CompletableFuture.supplyAsync({
			->
			LOGGER.info("Inside Async loop .....")
			def billingScheduleDueDate
			def billingScheduleAvailableDate
			def scheduleMap = [:]
			def billingSchedule = [:]
			def billHistory = [:]

			def billContact = [:]
			billContact = retrieveBillContactFromSPI(registeredServiceInvoker, spiPrefix, employeeId, spiMockURI, spiHeadersMap)
			if(null!=billContact){
				saveBillContacttoDB(entityService,billContact,tenantId, employeeId)
			}
			employeeEffectiveDate = employeesDetails?.item.extension.effectiveDate  //doubt
			LOGGER.info("employeeEffectiveDate... "+employeeEffectiveDate+"first element "+employeeEffectiveDate[0])
			def effectiveDateResponse = setEffectiveDate(employeeEffectiveDate[0])
			 billingScheduleDetails =retrieveBillingScheduleFromSPI(registeredServiceInvoker, spiPrefix, employeeId,spiMockURI, effectiveDateResponse.getAt("startDate"), effectiveDateResponse.getAt("endDate"),spiHeadersMap) //getTestData('employeeBillingSchedules-items.json')?.items.item //
			 def cal = Calendar.getInstance();
			 cal.add(Calendar.YEAR, -1);
			 def billFromDate = cal.getTime();
			 billFromDate = new SimpleDateFormat(BillingConstants.DATE_FORMAT).format(billFromDate)
			 billHistory =retrieveBillSummaryFromSPI(registeredServiceInvoker,billFromDate, spiPrefix, employeeId, spiMockURI, spiHeadersMap)
			 def response = setPastStatementFlag(employeeEffectiveDate, billingScheduleDetails)
			 for(res in response){
			 LOGGER.info "res--------"+ res
			 if (!res.isEnabled){
			 LOGGER.info "res.isEnabled--------"+ res
			 billingScheduleDueDate = res.dueDate
			 LOGGER.info "billingScheduleDueDate--------"+ billingScheduleDueDate
			 billingScheduleAvailableDate = res.scheduledSendDate
			 LOGGER.info "billingScheduleAvailableDate--------"+ billingScheduleAvailableDate
			 break;
			 }
			 }
			 if (billHistory != null) {
			 saveBillingHistorytoDB(entityService,billHistory,tenantId, employeeId, employeeEffectiveDate)
			 }
			 if (billingScheduleDetails != null) {
			 saveBillingScheduleDetailstoDB(entityService,billingScheduleDetails,tenantId, employeeId)
			 }
			 scheduleMap << [ 'payrollDeduction' : true ]
			 scheduleMap << [ 'groupNumber' : employeeId]
			 billingSchedule << [ 'billingAvailableDate' : billingScheduleAvailableDate]
			 billingSchedule << [ 'billingDueDate' : billingScheduleDueDate]
			 scheduleMap << [ 'billingSchedule' : billingSchedule ]
			 def billProfile = [:]
			 billProfile << [ 'billingDeductionSchedule' : billingScheduleDetails ]
			 def eventDataMapBillingScheduleList = buildEventDataBillingScheduleList(scheduleMap, employeeId, tenantId)
			 callBillingScheduledListViewService(registeredServiceInvoker, viewSrvVip, eventDataMapBillingScheduleList, tenantId, employeeId)
			 def eventDataMapBillingSchedule = buildEventDataBillingSchedule(billProfile, employeeId, tenantId)
			 callViewService(registeredServiceInvoker, viewSrvVip, eventDataMapBillingSchedule, tenantId, employeeId)
			 def paymentHistoryDetails =	retrievePaymentHistoryFromSPI(registeredServiceInvoker, spiPrefix, employeeId,spiMockURI, spiHeadersMap)
			 def paymentHistoryDetailed = retrievePaymentHistoryDetailedFromSPI(registeredServiceInvoker, spiPrefix, employeeId,spiMockURI, spiHeadersMap)
			 if (paymentHistoryDetailed != null) {
			 savePaymentHistoryDetailedtoDB(entityService,paymentHistoryDetailed,tenantId, employeeId)
			 }
			 def eventDataMapPaymentHistory = buildEventDataPaymentHistory(paymentHistoryDetails, employeeId, tenantId)
			 callPaymentHistoryListViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPaymentHistory, tenantId, employeeId)
			 def paymentHistoryMap =[:]
			 paymentHistoryMap << [ 'billingDeductionSchedule' : paymentHistoryDetailed ]
			 paymentHistoryMap << [ 'years':getYears(employeeEffectiveDate[0])]
			 def eventDataMapPaymentHistoryDetailed = buildEventDataPaymentHistoryDetailed(paymentHistoryMap, employeeId, tenantId)
			 callPaymentHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPaymentHistoryDetailed, tenantId, employeeId)
			 

			LOGGER.info("Before billNumber:------------- ")
			def billNumber = employeesDetails[0]?.item?.number
			LOGGER.info("billNumber:------------- " + billNumber)

			def premiumDetails = retrievePremiumDetailsFromSPI(registeredServiceInvoker, spiPrefix, employeeId, spiMockURI, spiHeadersMap, billNumber)
			def premiumDetailsReponse = premiumDetails
			LOGGER.info("IIB Response: " + premiumDetails)
			LOGGER.info("IIB Response premiumDetailsReponse: " + premiumDetailsReponse)

			if(premiumDetails !=null){
				LOGGER.info("inside")
				savePremiumDetailstoDB(entityService,premiumDetails,tenantId, employeeId)
			}else{
				LOGGER.info("Member premium details are null for employee.")
			}

			def eventDataMapPremiumDetails = buildEventDataPremiumDetails(premiumDetails, employeeId, tenantId, entityService)
			if(eventDataMapPremiumDetails) {
				LOGGER.info("inside eventDataMapPremiumDetails-----------")
				callPremiumDetailsViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPremiumDetails, tenantId, employeeId)
			}
		})

		workFlow.addResponseStatus(HttpStatus.OK)
		workFlow.addResponseBody(new EntityResult([response:'final response'], true))
	}
	/**
	 *
	 * @param resourceContents
	 * @param groupNumber
	 * @param tenantId
	 * @return
	 */
	def buildEventDataPremiumDetails(resourceContents, employeeId, tenantId, entityService){
		LOGGER.info "inside buildEventDataPremiumDetails"
		def response = [:] as Map
		def eventDataMapGroup = [:]

		def resName = 'resourceName' + '_' + EVENT_TYPE_PREMIUM_DETAILS
		if(!resourceContents.empty) {
			response << ["metadata" : resourceContents.metadata]
			response << ["coverageDetails" : getCoverageDetails(resourceContents, employeeId, entityService, tenantId)]
			LOGGER.info("***response**** :" + response)

			eventDataMapGroup << [groupNumber:employeeId, viewType:BillingConstants.COLLECTION_NAME_EMPLOYEE_PREMIUM_DETAILS, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PREMIUM_DETAILS, tenantId: tenantId,
				resourceName: (resName), (resName):response]
			LOGGER.info "buildEventData Event Data " + eventDataMapGroup
		}
		eventDataMapGroup
	}
	def callPremiumDetailsViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPremiumDetails, tenantId, groupNumber){
		LOGGER.info("inside callPremiumDetailsViewService")
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/memberPremiumDetails"
		LOGGER.info("viewServiceURI---------->"+ viewServiceURI)
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(eventDataMapPremiumDetails), Map.class)
		LOGGER.info("Premium view response------->"+response)
		response
	}

	def getCoverageDetails(details, employeeId, entityService, tenantId){
		LOGGER.info("inside coverageDetails")
		def coverageDetails = []
		def totalPremium = 0.0
		def adjAmt = 0.0
		def premiumAmt = 0.0
		def memberCertificateList =[]
		details?.memberCertificates.each({ memberCertificate ->
			def memberCertificateResp = [:]
			LOGGER.info("details------>"+ memberCertificate)
			def receivableCode = memberCertificate?.receivableCode
			memberCertificateResp << [ "productName" :  memberCertificate?.product?.nameCode]
			memberCertificateResp << [ "receivableCode" : receivableCode ]
			memberCertificateResp << [ "typeCode" :  memberCertificate?.product?.typeCode]
			memberCertificateResp << [ 'adjustmentReasonCode' : memberCertificate?.adjustmentReasonCode ]
			memberCertificateResp << [ 'billMonth' : memberCertificate?.billMonth]
			premiumAmt = (memberCertificate?.employerPremiumAmount?.amount).toFloat() + (memberCertificate?.employeePremiumAmount?.amount).toFloat()

			adjAmt = (memberCertificate?.adjustmentAmounts[0]?.employerAdjustmentAmount?.amount).toFloat() + (memberCertificate?.adjustmentAmounts[0]?.employeeAdjustmentAmount?.amount).toFloat()

			LOGGER.info "memberCertificateResp------------------>" + memberCertificateResp
			memberCertificateResp << [ 'premiumAmt' : premiumAmt +  adjAmt]
			LOGGER.info "premiumAmt------------------>" + premiumAmt
			totalPremium += memberCertificateResp?.premiumAmt
			LOGGER.info("Coverage MemberCertificate Details: "+"memberCertificateResp: "+memberCertificateResp+"adjAmt"+adjAmt)
			if (premiumAmt == '0.0' ||  premiumAmt == 0.0 || premiumAmt.equals("0.0")) {
				LOGGER.info("Variance if block...")
				memberCertificateResp << [ 'variance' :  '']
			}
			else {
				LOGGER.info("Variance else block....")
				if (memberCertificateResp.premiumAmt > 0) {
					memberCertificateResp << [ 'variance' :  (adjAmt/memberCertificateResp.premiumAmt)*100]
				} else {
					memberCertificateResp << [ 'variance' :  (adjAmt/-(memberCertificateResp.premiumAmt))*100]
				}

				LOGGER.info("Coverage MemberCertificate Detail after adding variance------->"+ memberCertificateResp)
			}
			/*def adjDate = memberCertificate?.adjustmentDate
			def billDate = employeeEffectiveDate

			if (adjDate == null || adjDate == "" || adjDate.equals("")){
				memberCertificateResp << [ "adjustmentDate": billDate]
			}else{
				memberCertificateResp << [ "adjustmentDate": adjDate]
			}*/
			memberCertificateResp << [ "adjustmentDate": memberCertificate?.adjustmentDate]
			memberCertificateResp << [ "class": memberCertificate?.class?.number]
			//Set teir for non-tier based coverages based on receivable code else pass tier code as is
			def tierCode = DownloadUtil.setTierBasedOnReceivableCode(receivableCode, memberCertificate?.tierCode)
			memberCertificateResp << [ "tier": tierCode]
			memberCertificateResp << [ "volume": memberCertificate?.volume]
			memberCertificateList.add(memberCertificateResp)
			LOGGER.info("memberCertificateList------>"+ memberCertificateList)
		})

		def billContactresponse = getBillContactResponseFromDB(employeeId,entityService,tenantId)
		def adjResp = [:]
		adjResp << ["memberCertificates" : memberCertificateList]
		adjResp << ["firstName" : billContactresponse?.data?.name?.firstName]
		adjResp << ["lastName" : billContactresponse?.data?.name?.lastName]
		adjResp << ["number" : employeeId]
		adjResp << ["totalPremium" : totalPremium]
		coverageDetails.add(adjResp)
		LOGGER.info("final coverageDetails------>"+ coverageDetails)


		coverageDetails
	}

	/**
	 *
	 * Used to get bill profile details from SPI
	 *
	 * @param registeredServiceInvoker
	 * @param spiPrefix
	 * @param employeeId
	 * @param spiMockURI
	 * @param spiHeadersMap
	 * @return
	 */

	def retrieveEmployeesBillProfileDetailsFromSPI(registeredServiceInvoker, spiPrefix,employeeId,spiMockURI, spiHeadersMap) {
		LOGGER.info("Entered retrieveEmployeesBillProfileDetailsFromSPI...")
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billProfiles?q=accountNumber==$employeeId"
		} else {
			uri = "${spiPrefix}/groups/employees/$employeeId/billProfiles?q=view==current"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			response = responseData.getBody().items

		} catch (Exception e) {
			LOGGER.error "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		LOGGER.info("Exit retrieveEmployeesBillProfileDetailsFromSPI...")
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
	protected saveBillProfileDetailstoDB(entityService,billProfileDetails,tenantId, employeeId) {
		LOGGER.info("Entered saveBillProfileDetailstoDB...")
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_PROFILE, employeeId)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status' + '_' + EVENT_TYPE_GROUP
			def accountDetailRes = [_id:employeeId, data: billProfileDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_PROFILE, accountDetailRes)
			TenantContext.cleanup()
		} catch (e) {
			LOGGER.error('Error saving Bill Preference ' +"${e.message}")
		}
		LOGGER.info("Exit saveBillProfileDetailstoDB...")
	}

	/**
	 * saving bill contact of employee
	 * @param entityService
	 * @param billContact
	 * @param tenantId
	 * @param employeeId
	 * @return
	 */
	protected saveBillContacttoDB(entityService,billContact,tenantId, employeeId) {
		LOGGER.info("Entered saveBillContacttoDB...")
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_BILLING_CONTACT_EMPLOYEE, employeeId)
			LOGGER.error('Inside Billing Contact Employee  DB')
			def status = 'status' + '_' + EVENT_TYPE_BILLING_CONTACT
			def accountDetailRes = [_id:employeeId, data: billContact, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_BILLING_CONTACT_EMPLOYEE, accountDetailRes)
			TenantContext.cleanup()
		} catch (e) {
			LOGGER.error('Error saving Billing contact employee ' +"${e.message}")
		}
		LOGGER.info("Exit saveBillContacttoDB...")
	}

	/**
	 * used to get employee bill contact details
	 * @param employeeId
	 * @param entityService
	 * @param tenantId
	 * @return
	 */
	def getBillContactResponseFromDB(employeeId,entityService,tenantId){
		def response
		def data = []
		try{
			response = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_BILLING_CONTACT_EMPLOYEE, employeeId, data)
		}catch(Exception e){
			LOGGER.error("Unable to get BillContact response:"+e.getMessage())
			response = ''
		}
		response
	}

	def deleteById(entityService,collectionName, employeeId) {
		LOGGER.info("Entered deleteById (Collection : "+collectionName+")...")
		try {
			entityService.deleteById(collectionName,employeeId)
		}catch(Exception e) {
			LOGGER.info("No data to delete: "+e.getMessage())
		}
		LOGGER.info("Exit deleteById...")
	}

	/*
	 * Used to set set effective date
	 */
	def setEffectiveDate(effectiveStartDate){
		LOGGER.info("Entered setEffectiveDate...")
		LOGGER.info("effectiveStartDate: "+effectiveStartDate)
		def responseMap = [:]
		try{
			LOGGER.info("inside setEffectiveDate")
			def year = Calendar.getInstance().get(Calendar.YEAR)
			def stDate = new Date().parse("MM/dd/yyyy", effectiveStartDate)
			def diffYears  = year - stDate.getAt(Calendar.YEAR)
			def sDate
			def eDate

			use(TimeCategory) {
				sDate = stDate + diffYears.year
				eDate =  sDate + 1.year - 1.day
			}
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy")
			def startDate = formatter.format( sDate );
			def endDate = formatter.format( eDate );
			LOGGER.info("setEffectiveDate startDate: "+startDate+" setEffectiveDate endDate: "+endDate)
			responseMap <<['startDate':startDate]
			responseMap <<['endDate': endDate]
		}catch(Exception e) {
			LOGGER.error("Exception inside setPastStatementFlag"+e.getMessage())
		}
		LOGGER.info("Exit setEffectiveDate...")
		responseMap
	}
	def retrieveBillingScheduleFromSPI(registeredServiceInvoker, spiPrefix, employeeId,spiMockURI, startDate, endDate, spiHeadersMap){
		LOGGER.info("Entered retrieveBillingScheduleFromSPI...")
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billingSchedules?q=number==$employeeId"
		} else {
			uri = "${spiPrefix}/groups/employees/$employeeId/billingSchedules?q=BillSchedulePeriodFrom==$startDate;BillSchedulePeriodTo==$endDate"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			response = responseData.getBody().items.item
		} catch (e) {
			LOGGER.error "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		LOGGER.info("Exit retrieveBillingScheduleFromSPI...")

		response
	}

	def retrieveBillContactFromSPI(RegisteredServiceInvoker registeredServiceInvoker, spiPrefix,employeeId,spiMockURI, spiHeadersMap) {

		LOGGER.info("Entered retrieveBillContactFromSPI...")
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/groups/employees/$employeeId/billingContact"
		} else {
			uri = "${spiPrefix}/groups/employees/$employeeId/billingContact"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			response = responseData.getBody().item
			LOGGER.info("Response from IIB billingContact :"+response)

		} catch (Exception e) {
			LOGGER.error "ERROR while retrieving bill contact details from SPI....."+e.toString()
		}
		LOGGER.info("Exit retrieveBillContactFromSPI...")
		response
	}


	def retrieveBillSummaryFromSPI(registeredServiceInvoker,billFromDate, spiPrefix, employeeId, spiMockURI, spiHeadersMap) {
		LOGGER.info("Entered retrieveBillSummaryFromSPI...")
		def uri
		def todaysDate = new Date().format( 'MM/dd/yyyy' )
		LOGGER.info('todaysDate retrieveBillSummaryFromSPI....' +todaysDate)
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billProfiles?q=number==$employeeId"
			LOGGER.info('uri retrieveBillSummaryFromSPI if....' +uri)
		} else {
			uri = "${spiPrefix}/groups/employees/$employeeId/billProfiles?q=billFromDate==$billFromDate;billToDate==$todaysDate;view==history"
			LOGGER.info('uri retrieveBillSummaryFromSPI else....' +uri)
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			response = responseData.getBody().items
			LOGGER.info('responseData retrieveBillSummaryFromSPI final....' +responseData)
		} catch (Exception e) {
			LOGGER.info "ERROR WHILE retrieving bill summary from SPI....."+e.toString()
		}
		LOGGER.info("Exit retrieveBillSummaryFromSPI...")
		response
	}

	/*
	 * Used to set past statement flag
	 */
	def setPastStatementFlag(effectiveStartDate, billingScheduleDetails){
		LOGGER.info("Entered setPastStatementFlag...")
		try{
			LOGGER.error("inside ")
			def year = Calendar.getInstance().get(Calendar.YEAR)
			def stDate = new Date().parse("MM/dd/yyyy", effectiveStartDate[0])
			use(TimeCategory) {
				billingScheduleDetails.each({billingScheduleDetail ->
					def actualSendDate = billingScheduleDetail.actualSendDate
					if (actualSendDate != null){
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
		LOGGER.info("Exit setPastStatementFlag...")
		billingScheduleDetails
	}
	/**
	 * Used to save the billing History details in billingHistory_Employee collection in DB
	 *
	 * @param entityService
	 * @param billingScheduleDetails
	 * @param tenantId
	 * @param employeeId
	 * @param effectiveDate
	 * @return
	 */
	protected saveBillingHistorytoDB(entityService,billingScheduleDetails,tenantId, employeeId, effectiveDate) {
		LOGGER.info("Entered saveBillingHistorytoDB")
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_HISTORY, employeeId)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status' + '_' + EVENT_TYPE_BILLING_HISTORY
			def billHistory = [:]
			billHistory << ['billingScheduleDetails' : billingScheduleDetails ]
			billHistory << ['years' :getYears(effectiveDate) ]
			def accountDetailRes = [_id:employeeId, data: billHistory,  (status):'NA']

			entityService.create(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_HISTORY, accountDetailRes)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saveBillingHistorytoDB ' +"${e.message}")
			throw new GSSPException("20004")
		}
		LOGGER.info("Exit saveBillingHistorytoDB")
	}
	/**
	 * Used to save the billing schedule details in billingSchedule_Employee collection in DB
	 *
	 * @param entityService
	 * @param billingScheduleDetails
	 * @param tenantId
	 * @return
	 */
	protected saveBillingScheduleDetailstoDB(entityService,billingScheduleDetails,tenantId, employeeId) {
		LOGGER.info("Entered saveBillingScheduleDetailstoDB")
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_EMPLOYEE_BILLING_SCHEDULE, employeeId)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status' + '_' + EVENT_TYPE_BILLING_SCHEDULE
			def accountDetailRes = [_id:employeeId, data: billingScheduleDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILLING_SCHEDULE, accountDetailRes)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving billing Schedule ' +"${e.message}")
			throw new GSSPException("20004")
		}
		LOGGER.info("Exit saveBillingScheduleDetailstoDB")
	}
	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataBillingSchedule(resourceContents, employeeId, tenantId){
		LOGGER.info("Entered buildEventDataBillingSchedule...")
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_BILLING_SCHEDULE
		eventDataMapGroup << [groupNumber:employeeId, viewType:BillingConstants.COLLECTION_NAME_EMPLOYEE_BILLING_SCHEDULE, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_BILLING_SCHEDULE, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		LOGGER.info("Exit buildEventDataBillingSchedule...")
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
	def callViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, employeeId){
		LOGGER.info("Entered callViewService...")

		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$employeeId/billingScheduleSummary"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		LOGGER.info("Exit callViewService...")
		response
	}
	/*
	 * Used to get bill profile details from SPI
	 */
	def retrievePaymentHistoryFromSPI(registeredServiceInvoker, spiPrefix, employeeId,spiMockURI, spiHeadersMap) {
		LOGGER.info("Entered retrievePaymentHistoryFromSPI...")
		def uri
		LOGGER.info('inside payment spi call 1')
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/payments?q=number==$employeeId"
		} else {
			uri = "${spiPrefix}/groups/employees/$employeeId/payments"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info('final service uri is::'+serviceUri)
		def response
		def numberOfTransaction
		def paymentTransaction = 0
		def refundTransaction = 0
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			LOGGER.info('responseData payment history is::'+serviceUri)
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
		LOGGER.info("Exit retrievePaymentHistoryFromSPI...")
		LOGGER.info(' final responseData payment history is::'+serviceUri)
		response
	}
	/*
	 * Used to get bill profile details from SPI
	 */
	def retrievePaymentHistoryDetailedFromSPI(registeredServiceInvoker, spiPrefix, employeeId,spiMockURI, spiHeadersMap) {
		LOGGER.info("Entered retrievePaymentHistoryDetailedFromSPI...")
		def uri
		LOGGER.info('inside payment history call 2')
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/payments?q=number==$employeeId"
		} else {
			uri = "${spiPrefix}/groups/employees/$employeeId/payments"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info('service uri is::'+serviceUri)
		def response
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			LOGGER.info('paymentHistoryDetailed response from spi data is::' +responseData)
			response = responseData.getBody().items.item
			LOGGER.info('SPI Response extracted item'+ response)
		} catch (e) {
			LOGGER.error "ERROR while retrieving paymentHistory details from SPI....."+e.toString()
		}
		LOGGER.info("Exit retrievePaymentHistoryDetailedFromSPI...")
		response
	}

	/**
	 * Used to save the PaymentHistoryDetailes in PaymentHistory_Employee collection in DB
	 * @param entityService
	 * @param billingScheduleDetails
	 * @param tenantId
	 * @param employeeId
	 * @return
	 */
	protected savePaymentHistoryDetailedtoDB(entityService,billingScheduleDetails,tenantId, employeeId) {
		LOGGER.info("Entered savePaymentHistoryDetailedtoDB...")
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_EMPLOYEE_PAYMENT_HISTORY, employeeId)
			LOGGER.error('Inside Bill Profile DB')
			def status = 'status'+'_' + EVENT_TYPE_PAYMENT_HISTORY_DETAILED
			def accountDetailRes = [_id:employeeId, data: billingScheduleDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_EMPLOYEE_PAYMENT_HISTORY, accountDetailRes)
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving paymentHistory ' +"${e.message}")
			throw new GSSPException("20004")
		}
		LOGGER.info("Exit savePaymentHistoryDetailedtoDB...")
	}
	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataPaymentHistory(resourceContents, employeeId, tenantId){
		LOGGER.info("Entered buildEventDataPaymentHistory...")
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_PAYMENT_HISTORY
		eventDataMapGroup << [groupNumber:employeeId, viewType:BillingConstants.COLLECTION_NAME_EMPLOYEE_PAYMENT_HISTORY, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PAYMENT_HISTORY, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		LOGGER.info("Exit buildEventDataPaymentHistory...")
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
	def callPaymentHistoryListViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, employeeId){
		LOGGER.info("Entered callPaymentHistoryListViewService...")
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$employeeId/paymentTransactionList"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		LOGGER.info("Exit callPaymentHistoryListViewService...")
		response
	}
	def getYears(date){
		LOGGER.info("Entered getYears...")
		DateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		def currentYear = Calendar.getInstance().get(Calendar.YEAR)
		def year = sdf.parse(date).getAt(Calendar.YEAR)
		def diffYears  = currentYear - year
		def years = []
		for (int i=0;i<=diffYears;i++){
			years.add(year+i)
		}
		LOGGER.info("Exit getYears...")
		years
	}
	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataPaymentHistoryDetailed(resourceContents, employeeId, tenantId){
		LOGGER.info("Entered buildEventDataPaymentHistoryDetailed...")
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_PAYMENT_HISTORY_DETAILED
		eventDataMapGroup << [groupNumber:employeeId, viewType:BillingConstants.COLLECTION_NAME_EMPLOYEE_PAYMENT_HISTORY, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PAYMENT_HISTORY_DETAILED, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		LOGGER.info("Exit buildEventDataPaymentHistoryDetailed...")
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
	def callPaymentHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, employeeId){
		LOGGER.info("Entered callPaymentHistoryDetailedViewService...")
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$employeeId/paymentTransactionSummary"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
		LOGGER.info("response from callPaymentHistoryDetailedViewService is:"+response)
		LOGGER.info("Exit callPaymentHistoryDetailedViewService...")
		response
	}
	def retrievePremiumDetailsFromSPI(registeredServiceInvoker, spiPrefix, employeeId,spiMockURI, spiHeadersMap, billNumber) {
		LOGGER.info("Entered retrievePremiumDetailsFromSPI...")
		def uri
		def response
		def membeResponse
		billNumber = ''+billNumber
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/memberCertificates?q=number==$employeeId"
		} else {
			uri = "${spiPrefix}/groups/employees/$employeeId/memberCertificates?q=billNumber==$billNumber"
			LOGGER.info("uri is: " + uri)
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info("final url is: " + serviceUri)
		try {
			LOGGER.info("inside try block ")
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			LOGGER.info("getViaSPI: -----------------" + response)
			if(response.getBody() != null) {
				membeResponse = response.getBody()
				LOGGER.info("MemberCertificateResponse is: " + response)
				LOGGER.info("response.getBody() is: " + response.getBody())
				LOGGER.info("membeResponse is: " + membeResponse)
			}
		} catch (e) {
			LOGGER.info("ERROR while retrieving premium details from SPI....."+e.toString()) //changed to info log as error log is not workinng
		}
		LOGGER.info("Final Reponse from spi calls...")
		membeResponse
	}
	def savePremiumDetailstoDB(entityService,premiumDetails,tenantId, employeeId) {
		LOGGER.info("Entered savePremiumDetailstoDB...")
		try{
			TenantContext.setTenantId(tenantId)
			deleteById(entityService, BillingConstants.COLLECTION_NAME_EMPLOYEE_PREMIUM_DETAILS, employeeId)
			LOGGER.info('Inside Bill Profile DB')
			def status = 'status'+'_'+ EVENT_TYPE_PREMIUM_DETAILS
			def accountDetailRes = [_id:employeeId, data: premiumDetails, (status):'NA']
			entityService.create(BillingConstants.COLLECTION_NAME_EMPLOYEE_PREMIUM_DETAILS, accountDetailRes)
			TenantContext.cleanup()
		}catch(e){
			LOGGER.info('Error saving premiumDetails ' +"${e.message}")
		}
		LOGGER.info("Exit savePremiumDetailstoDB...")
	}
	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataBillingScheduleList(resourceContents, employeeId, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_DEDUCTION_SCHEDULE
		eventDataMapGroup << [groupNumber:employeeId, viewType:BillingConstants.COLLECTION_NAME_EMPLOYEE_BILLING_SCHEDULE, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_DEDUCTION_SCHEDULE, tenantId: tenantId,
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
	def callBillingScheduledListViewService(registeredServiceInvoker, viewSrvVip, billingScheduleDetails, tenantId, employeeId){
		LOGGER.info("Entered callBillingScheduledListViewService...")
		LOGGER.error("inside callBillingScheduledListViewService.....")
		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$employeeId/billingScheduleList"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingScheduleDetails), Map)
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
		def file = new FileReader(workingDir + "/src/test/data/"+fileName)
		Object obj = parser?.parse(file)
		jsonObj = (HashMap<String, Object>) obj;
		return jsonObj;
	}
}
