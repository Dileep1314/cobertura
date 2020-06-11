package groovy.US

import static java.util.UUID.randomUUID
import java.text.DecimalFormat
import java.util.Map

import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import com.metlife.service.entity.EntityService
import com.metlife.service.entity.GSSPEntityService
import com.metlife.utility.TenantContext

import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.ValidationUtil
import net.minidev.json.parser.JSONParser


class GET_SELF_ADMIN_BILL_PROFILE implements Task{

	private static final Logger LOGGER = LoggerFactory.getLogger(GET_SELF_ADMIN_BILL_PROFILE)

	@Override
	Object execute(WorkflowDomain workFlow) {
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def groupNumber = requestPathParamsMap[BillingConstants.ID]
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		ValidationUtil validation = new ValidationUtil()
		 def validationList = [] as List
		 validationList.add(groupNumber)
		 validation.validateUser(workFlow, validationList)
		def requestParamsMap = workFlow.getRequestParams()
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList?.tokenize(BillingConstants.COMMA) , requestHeaders)
		def billNumber
		def submissionStatus
		def view
		def isUpdated
		if (requestParamsMap.get('q')!=null) {

			def requestParam = requestParamsMap.get('q')
			def (key, value) = requestParam.tokenize(";").each{queryParam ->
				def (key, value) = queryParam.tokenize( '==' )
				if (key != null && key == "number") {
					billNumber = value
				}
				if (key != null && key == "submissionStatus") {
					submissionStatus = value
				}
				if(key != null && key == "view"){
					view = value
				}
				if(key != null && key == "isUpdated"){
					isUpdated = value
				}
			}
		}
		// Below code gets executed if update actuals is successfull and to persist latest updated actuals in mongoDB
		if (isUpdated != null && isUpdated == '1') {
			def response
			// check for current bill to determine mongoDB collection to update
			boolean isCurrent  = isLatestBill(entityService,tenantId, groupNumber, billNumber)
			response = getSelfAdminDetailsFromSPI(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber,  billNumber, spiHeadersMap, isCurrent)
			if (response != null && !response.isEmpty()) {
				response = response[0]
				updateResource(entityService, response, tenantId, groupNumber, billNumber, isCurrent)
			} else {
				throw new GSSPException("20018")
			}
		} else {
			// Below code gets executed to return all actuals present for the given bill number
			def response
			//step1: retrieve self admin draft data if present
			def draftInfo = getDraftedInfo(entityService, groupNumber, billNumber)


			if (draftInfo != null && !draftInfo.isEmpty()) {
				response = draftInfo
			} else {
				//step2: If no record found then based on view either look for record in DB or call IIB

				if (view != null && view.equals("history")) {
					response = getSelfAdminDetailsFromSPI(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber,  billNumber, spiHeadersMap, false)
				} else {
					response = getSelfAdminDetails(entityService, groupNumber)
				}

			}

			if (response == null || response.isEmpty()) {
				throw new GSSPException("20018")
			}
			def billMethodCode
			def finalResposne =[:] as Map
			response.each({resp ->
				billMethodCode = resp.extension.billMethodCode
				resp?.extension << ['billMethodCode':getBillingMethod(billMethodCode, config, 'ref_billing_form_data')]
				resp?.extension << ['billStatusTypeCode':getBillingMethod(resp.extension.billStatusTypeCode, config, 'ref_billstatustypecode')]
				resp << ['billMode':getBillingMethod(resp.billMode, config,'ref_billing_form_data')]
				resp?.extension << ['submissionStatusTypeCode':getBillingMethod(resp.extension.submissionStatusTypeCode, config, 'ref_billing_form_data')]
				def prod = resp?.getAt('extension')?.getAt('productDetails')
				prod.each({product ->
					def typeCode = product?.product?.typeCode
					def receivableCode
					def receivable = product?.getAt('receivable')
					receivable.each({recieve->
						def tierCode
						tierCode = recieve?.getAt('tierCode')
						receivableCode = recieve?.getAt('receivableCode')
						//Set teir for non-tier based coverages based on receivable code else pass tier code as is
						tierCode = DownloadUtil.setTierBasedOnReceivableCode(receivableCode, tierCode)
						if (tierCode != null && !tierCode.isEmpty()){
							if (tierCode.contains('Spouse') || tierCode.contains('Children')) {
								recieve << ['tierCode':tierCode]
							} else {
								recieve << ['tierCode':getBillingMethod(tierCode, config, 'ref_self_admin_details')]
							}

						}
					})
					// put recievable code and its name in product object as requested by UI.
					if (receivableCode != null) {
						product.product << ['receivableCode': receivableCode]
						product.product << ['receivableCodeName': getBillingMethod(receivableCode, config, 'ref_self_admin_details')]
					}
					if (typeCode != null && !typeCode.isEmpty()) {
						product.product << ['typeCode' : getBillingMethod(typeCode, config, 'ref_self_admin_details')]
					}

				})

				resp.remove('_id')
				resp.remove('self')
				resp.remove('isSubmittedActuals')
				resp.remove('updatedAt')
				finalResposne << ['Response': resp]
			})

			def data = finalResposne.getAt('Response')
			def feeAmounnts = data.extension.feeAmounts
			def feeTypeCode
			if(!feeAmounnts.isEmpty()){
				for(def feeAmountData:feeAmounnts){
					feeTypeCode = feeAmountData.feeTypeCode

					if (feeTypeCode == "201") {
						feeAmountData.feeTypeCode = 'Billing Fee'
					} else if (feeTypeCode == "202") {
						feeAmountData.feeTypeCode = "Non-Sufficient Fund Fee"
					}

				}
			}
			workFlow.addResponseBody(new EntityResult([selfAdminDetails:data], true))
		}


		workFlow.addResponseStatus(HttpStatus.OK)
	}



	def getBillingMethod(code , config, configurationId){
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		def billMethodMap=[:]
		billMethodMap = statusMapping?.data
		billMethodMap[code.toString()]
	}



	/**
	 * Used to retrieve current/latest bill details from MongoDB billProfileSubGroup collection to show selfadmin actuals
	 *
	 * @param entityService
	 * @param groupNumber
	 *
	 * @return billProfileData
	 */
	def getSelfAdminDetails(entityService, groupNumber) {
		def billProfileData
		try {
			Criteria inCriteria = Criteria.where("_id").is(groupNumber)
			billProfileData = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, inCriteria).data.item
		} catch (e) {
			LOGGER.error('Error retrieving self admin actuals : ' +"${e.message}")
		}
		billProfileData
	}


	/**
	 * calling SPI to get history of bill profile
	 * @param registeredServiceInvoker
	 * @param spiPrefix
	 * @param spiMockURI
	 * @param groupNumber
	 * @param billNumber
	 * @param spiHeadersMap
	 * @return
	 */
	def getSelfAdminDetailsFromSPI(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber,  billNumber, spiHeadersMap, isCurrent) {

		try {
			def response
			def finalResp =  []
			def uri
			if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
				uri = "${spiPrefix}/selfAdminBillProfile"
			} else if(isCurrent)
			{

				uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=view==current";
			} else {
				uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=billNumber==$billNumber;view==history";
			}
			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			def serviceUri = uriBuilder.build(false).toString()
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:],spiHeadersMap)
			LOGGER.info("Selfadmin SPI response: "+response)
			if (response != null) {
				LOGGER.info("response body: "+response.getBody().items)
				response = response.getBody()?.items[0]?.item
				LOGGER.info("response body after remove item: "+response)
				(response != null && !response.isEmpty()) ? finalResp.add(response): null
			}
			return finalResp

		} catch (Exception e) {
			LOGGER.error 'Exception in getting data from SPI for selfAdmin billProfile ' + e.toString()
			throw new GSSPException("20019")
		}

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
	def getDraftedInfo(entityService, groupNumber, billNumber) {
		def draftResp
		try{
			Criteria inCriteria = Criteria.where("_id").is(billNumber)
			inCriteria.where("data.item.accountNumber").is(groupNumber)
			draftResp = entityService.listByCriteria('selfAdminDraft', inCriteria).data.item
			LOGGER.info("draftResp from selfAdminDraft is :"+draftResp)
		} catch(Exception exp) {
			LOGGER.error("error while retrieving self admin drafted data from DB "+exp.getMessage())
		}
		draftResp
	}



	private Map<String, Object> getTestData(String fileName) {
		JSONParser parser = new JSONParser();
		Map<String, Object> jsonObj = null;
		String workingDir = System.getProperty("user.dir");
		Object obj = parser.parse(new FileReader(workingDir+"/src/test/data/"+fileName));
		jsonObj = (HashMap<String, Object>) obj;
		return jsonObj;
	}

	def isLatestBill(entityService,tenantId, groupNumber, billNumber) {

		def currBill
		def dbResponse
		boolean isLatestBill = false
		try {
			dbResponse = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, groupNumber, []).data?.item
		} catch (Exception exp) {
			LOGGER.error("isLatestBill: Exception while retrieving datafrom collection billProfileSubGroup "+exp.getMessage())
		}
		if (dbResponse != null) {
			currBill = dbResponse?.number
			if (currBill == billNumber)
				isLatestBill = true
		}

		isLatestBill
	}
	def updateResource(GSSPEntityService entityService, SPIresponse, tenantId, groupNumber, billNumber, isCurrent){
		try{
			def data = ['data.item' : SPIresponse]

			if (isCurrent) {
				def searchQuery = ['_id': groupNumber,'data.item.accountNumber': groupNumber,'data.item.number': billNumber]
				TenantContext.setTenantId(tenantId)
				entityService.updateByQuery(BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, searchQuery, data)
			}
			Criteria  citeria = Criteria.where("_id").is(groupNumber)
			def historyBill = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_HISTORY, citeria).data.billingScheduleDetails[0]
			if (historyBill != null && !historyBill.isEmpty()) {
				for (def billInfo : historyBill) {
					if (billInfo?.item?.number == billNumber) {
						SPIresponse?.extension?.remove('productDetails')
						LOGGER.info("spi response after remove productDetails: "+ SPIresponse)
						billInfo?.item = SPIresponse
						def searchQuery = ['_id': groupNumber,'data.billingScheduleDetails.item.accountNumber': groupNumber,'data.billingScheduleDetails.item.number': billNumber]
						data = ['data.billingScheduleDetails' : historyBill]
						entityService.updateByQuery(BillingConstants.COLLECTION_NAME_BILL_HISTORY, searchQuery, data)
						break
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error('Error saving actual bill data ' +"${e.message}")
			throw new GSSPException("20004")
		}
	}
}


