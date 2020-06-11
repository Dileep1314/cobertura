package groovy.US

/**
 * This groovy is used to update the billing
 * preference details
 *
 * Call : SPI
 */

import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.taskflow.Task
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.exception.GSSPException
import static java.util.UUID.randomUUID

import com.metlife.service.entity.EntityService
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.utility.TenantContext

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil


/**
 * @author Pramit
 *
 */
class EDIT_BILLING_PREFERENCE implements Task {

	private static final Logger LOGGER = LoggerFactory.getLogger(EDIT_BILLING_PREFERENCE)
	def deliveryMethodName
	def deliveryMethodCode

	@Override
	Object execute(WorkflowDomain workFlow) {
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
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]
	
        boolean isEmployee = false
		if(groupNumber == null){
			def employeId = requestPathParamsMap[BillingConstants.ID]
			isEmployee = true
			groupNumber = employeId;
		}
      
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
      
		def requestBody=workFlow.getRequestBody().updatedbillingPreferences
		LOGGER.info("requestBody1 :"+requestBody)
		def apexGroup = workFlow.getRequestBody().groupNumber

		if (apexGroup == null){
			apexGroup = groupNumber
		}
		LOGGER.info("GroupNumber :"+groupNumber+" apexGroup :"+apexGroup)
      
        def paymentMethod= requestBody?.paymentMethod
		def extension = [:]
		extension << ['paymentMethod' : paymentMethod]
		requestBody << ['extension': extension]
		requestBody.remove('paymentMethod')
		LOGGER.info("final requestBody from ui.......::::"+requestBody)
      
		editBillingPreference(requestBody , registeredServiceInvoker, groupNumber, spiPrefix,spiMockURI, spiHeadersMap, config, tenantId,isEmployee)
		def billProfile = retrieveBillProfileFromDB(entityService, apexGroup, tenantId,isEmployee)

		def billProfileData = billProfile.data
		billProfileData = updateBillProfileData(billProfileData, deliveryMethodCode, groupNumber)
		billProfile.putAt('data', billProfileData)
		if (!isEmployee) {
			def viewBillProfileGroup = billProfile.view_BILL_PROFILE_GROUP
			def detailedClientData = viewBillProfileGroup.detailedClientData.items
			detailedClientData = updateDetailedClientData(detailedClientData,deliveryMethodName,groupNumber)
			viewBillProfileGroup.detailedClientData.putAt('items', detailedClientData)
			billProfile.putAt('view_BILL_PROFILE_GROUP', viewBillProfileGroup)
		}

		updateBillProfileToDB(entityService,billProfile,tenantId, apexGroup,isEmployee)


		workFlow.addResponseStatus(HttpStatus.OK)
	}

	def editBillingPreference(requestBody,RegisteredServiceInvoker registeredServiceInvoker,groupNumber, spiPrefix,spiMockURI, spiHeadersMap, config, tenantId,isEmployee) {
		def response
		try {
			def uri
			if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
				uri= "${spiPrefix}/billingPreferences/$groupNumber"
				LOGGER.info("Group uri-------------->"+ uri)
			} else {
				if(isEmployee) {
					uri = "${spiPrefix}/groups/employees/$groupNumber/billingPreference"
					LOGGER.info("Employee uri-------------->"+ uri)
				}
				else{
					uri = "${spiPrefix}/groups/$groupNumber/billingPreference"
				}
			}
			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			LOGGER.info("uriBuilder-------------->"+ uriBuilder)
			def serviceUri = uriBuilder.build(false).toString()
			LOGGER.info("serviceUri-------------->"+ serviceUri)
			if(requestBody !=null){
				def delMethod = requestBody.deliveryMethod
				deliveryMethodName = delMethod
				LOGGER.info('Delovery method name is.. '+deliveryMethodName)
				def deliveryMethod = getBillingMethod(delMethod, config,'ref_billing_form_data')
				deliveryMethodCode = deliveryMethod
				LOGGER.info('Delovery method code is.. '+deliveryMethodCode)
				requestBody << ['deliveryMethod':deliveryMethod]
				def bilFreq = requestBody.billingFrequency
				def billingFrequency = getBillingMethod(bilFreq, config, 'ref_billing_form_data')
				requestBody << ['billingFrequency':billingFrequency]
				def bilBasis = requestBody.billBasis
				def billBasis = getBillingMethod(bilBasis, config, 'ref_billing_form_data')
				requestBody << ['billBasis': billBasis]
				def billMethod = requestBody.billMethodCode
				def billMethodCode = getBillingMethod(billMethod, config, 'ref_billing_form_data')
				requestBody << ['billMethodCode':billMethodCode]
              	def paymentMethod = requestBody.paymentMethod
    			LOGGER.info("paymentMethod.."+ paymentMethod)
    			requestBody.extension <<['paymentMethod': paymentMethod]
			}
			LOGGER.info("requestBody.."+ requestBody)
			HttpEntity<String> request = registeredServiceInvoker.createRequest(requestBody, spiHeadersMap)
			LOGGER.info("request.."+ request)
			response = registeredServiceInvoker.putViaSPIWithResponse(serviceUri, request, [:])
		} catch (Exception e) {
			throw new GSSPException("20009")
		}
		response
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

	def getBillingMethod(code , config, configurationId){
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		def billMethodMap=[:]
		billMethodMap = statusMapping?.data
		billMethodMap[code.toString()]
	}

	/**
	 * This method will fetch the data from DB for Update
	 * @param entityService
	 * @param groupNumber
	 * @param tenantId
	 * @return
	 */
	def retrieveBillProfileFromDB(entityService, groupNumber, tenantId,isEmployee)  {
		def data = []
		def billProfileDB
		LOGGER.info "groupNumber is::"+groupNumber
		try {
			data.add('data')
			data.add('view_BILL_PROFILE_GROUP')
			data.add('view_GROUP_DETAILS')
			if(isEmployee){
				billProfileDB = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_PROFILE, groupNumber, data)
				LOGGER.info "Employee billProfileDB--------------->"+billProfileDB
			}
			else{
				billProfileDB = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_BILL_PROFILE, groupNumber, data)
			}
			LOGGER.info "billProfileDB"+billProfileDB
		} catch (e) {
			LOGGER.error('Error retrieveBillSummaryFromDB ' +"${e.message}")
		}
		billProfileDB
	}

	/**
	 * This method will update the delivery method code for un-hydrated data 
	 * 
	 * @param billProfileData
	 * @param deliveryMethodCode
	 * @param groupNumber
	 * @return
	 */
	def updateBillProfileData(billProfileData, deliveryMethodCode, groupNumber) {
		try {
			for(def item : billProfileData) {
				if(item.item.accountNumber == groupNumber) {
					item.item.putAt('billMode', deliveryMethodCode)
				}
			}
		}catch(Exception e) {
			LOGGER.info("Error occoured while updating billProfileData")
		}
		billProfileData
	}

	/**
	 *  This method will update the data in DB
	 * @param entityService
	 * @param billProfileData
	 * @param tenantId
	 * @param groupNumber
	 * @return
	 */

	def updateBillProfileToDB(entityService,billProfileData,tenantId, groupNumber,isEmployee) {
		try{
			def searchQuery = ['_id':groupNumber]
			TenantContext.setTenantId(tenantId)
			if(isEmployee){
				entityService.updateByQuery(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_PROFILE, searchQuery, billProfileData)
			}
			else{
				entityService.updateByQuery(BillingConstants.COLLECTION_NAME_BILL_PROFILE, searchQuery, billProfileData)
			}
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving groupDetails ' +"${e.message}")
			throw new GSSPException("20004")
		}
	}

	/**
	 * This method will change the  deliveryMethodName in hydrated response
	 * @param detailedClientData
	 * @param deliveryMethodName
	 * @param groupNumber
	 * @return
	 */
	def updateDetailedClientData(detailedClientData,deliveryMethodName,groupNumber) {
		try {
			for(def item : detailedClientData) {
				def clientRowData = item.detailedClientRowData
				if(clientRowData[7].text == groupNumber) {
					clientRowData[4].putAt('text', deliveryMethodName)
				}
			}
		}catch(Exception e){
			LOGGER.info("Error occoured while updating billProfileData")
		}
		detailedClientData
	}
}


