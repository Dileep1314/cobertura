package groovy.US

import static java.util.UUID.randomUUID

import java.text.DateFormat
import java.text.SimpleDateFormat

import org.springframework.http.HttpEntity
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

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil


/**
 *
 * @author panchanan
 *
 */
class GET_BILLING_DETAILS_BY_YEAR implements Task {
	def static final EVENT_TYPE_GROUP = 'groupInfo'
	def static final EVENT_TYPE_BILLING_HISTORY = 'BILLING_HISTORY'
	def static final CHANNEL_ID = 'GSSPUI'
	private static final Logger logger = LoggerFactory.getLogger(GET_BILLING_DETAILS_BY_YEAR)
	def billMethodMap=[:]

	@Override
	public Object execute(WorkflowDomain workFlow) {
		def isVoidedActual = false
		def requestParamsMap = workFlow.getRequestParams()
		if (requestParamsMap.get('q')!=null) {
			def params = requestParamsMap?.get('q')?.tokenize(';')
			for(def p : params){
				def (key, value) = p.tokenize( '==' )
				if (key != null && key == "versionNumber") {
					isVoidedActual = true
					break
				}
			}
		}
		if(isVoidedActual){
			def response = callVoidedActualDetails(workFlow)
			workFlow.addResponseBody(new EntityResult([selfAdminVoidedActual:response], true))
			workFlow.addResponseStatus(HttpStatus.OK)
		}else{
			def reqColList = []
			def roleType
			if (requestParamsMap.get('q')!=null) {
				requestParamsMap?.get('q')?.tokenize(';').each{queryParam ->
					def (key, value) = queryParam.tokenize( '==' )
					if (key != null && key == "persona") {
						roleType = value
					}
					if (key != null && key == "classids") {
						reqColList = value.tokenize(',')
					}
				}
			}
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
			def groupNumber = requestPathParamsMap[BillingConstants.GROUP_ID]
			
			ValidationUtil validation = new ValidationUtil()
			def validationList = [] as List
			validationList.add(groupNumber)
			validation.validateUser(workFlow, validationList)
			
			def years = []
			def billingHistoryDetailed = retrieveBillSummaryFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,requestParamsMap, roleType,spiHeadersMap)
			logger.info "billingHistoryDetailed is.."+billingHistoryDetailed
			for(def bill : billingHistoryDetailed) {
				years << getYears(bill.item.billDate)
			}


			if (billingHistoryDetailed  == null) {
				throw new GSSPException("20010")
			}
			def billingHistoryMap =[:]
			billingHistoryMap << [ 'billingScheduleDetails' : billingHistoryDetailed ]
			def paramMap = [:]

			paramMap << ["101" : "101"]
			paramMap << ["102" : "102"]
			paramMap << ["103" : "103"]
			paramMap << ["104" : "104"]
			paramMap << ["105" : "105"]
			paramMap << ["106" : "106"]
			paramMap << ["107" : "107"]
			paramMap << ["108" : "108"]
			paramMap << ["109" : "109"]
			paramMap << ["110" : "110"]
			paramMap << ["111" : "111"]


			if(reqColList.size() > 0){
				paramMap = paramMap.subMap(reqColList)
			}

			billingHistoryMap.billingScheduleDetails.each({history ->
				history << ['paramMap' : paramMap]
			})
			billingHistoryMap << ['paramMap' : paramMap]
			billingHistoryMap << ['years' : years as Set]
			def eventDataMapBillingHistoryDetailed = buildEventDataBillingHistoryDetailed(billingHistoryMap, groupNumber, tenantId)
			def hydratedData = callBillingHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, eventDataMapBillingHistoryDetailed, tenantId, groupNumber)
			def clientsList=[]
			def response = [:]
			def pageValue
			def pageSize
			def totalResult
			def columns
			def configurationId
			def type
			def billSubmissionStatus
			def billSubmissionPage
			def finalYears = []
			if (hydratedData != null) {
				type = hydratedData.getBody().balanceSummary.type
				configurationId = hydratedData.getBody().balanceSummary.configurationId
				columns = hydratedData.getBody().balanceSummary.columns
				clientsList = hydratedData.getBody().balanceSummary.detailedBillHistory
				billSubmissionStatus = hydratedData.getBody().balanceSummary.billSubmissionStatus
				finalYears = hydratedData.getBody().balanceSummary.years
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
			billSubmissionPage = getPage(billSubmissionStatus,pageValue.toInteger(),pageSize.toInteger())
			logger.info "clientsList is.."+clientsList
			response << ['columns':columns]
			response << ['type':type]
			response << ['configurationId':configurationId]
			response << ['billSubmissionStatus' : billSubmissionPage]
			response << ['detailedBillHistory':clientsList]
			response << ['years':finalYears]
			response << ['offset':pageValue.toString()]
			response << ['limit':pageSize.toString()]
			response << ['totalSize':totalResult.toString()]
			workFlow.addResponseBody(new EntityResult([billingDetails :response], true))
			workFlow.addResponseStatus(HttpStatus.OK)
		}
	}

	def getPage(clientsList, page, pageSize) {
		clientsList.subList(page, Math.min(page + pageSize, clientsList.size()))
	}

	/**
	 * Used to build the customers event data to pass to view updater
	 *
	 * @param resourceContents
	 * @param accountNumber
	 * Build Event data for Group
	 * @return eventDataMapGroup
	 */
	def buildEventDataBillingHistoryDetailed(resourceContents, groupNumber, tenantId){
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_BILLING_HISTORY
		eventDataMapGroup << [groupNumber:groupNumber, viewType:'', accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_BILLING_HISTORY, tenantId: tenantId,
			resourceName:(resName), (resName):resourceContents]
		eventDataMapGroup
	}

	def retrieveBillSummaryFromSPI(registeredServiceInvoker, spiPrefix, groupNumber, requestParamsMap, def roleType, spiHeadersMap) {
		def queryMap = ""
		if (requestParamsMap.get('q')!=null) {
			requestParamsMap?.get('q')?.tokenize(';').each{queryParam ->
				def (key, value) = queryParam.tokenize( '==' )
				if (key != null && key == "year") {
					queryMap = "billFromDate==01/01/$value;billToDate==12/31/$value"
					logger.info("queryMap:::::"+ queryMap)
				}else {
					def cal = Calendar.getInstance();
					cal.add(Calendar.YEAR, -1);
					def billFromDate = cal.getTime();
					def todaysDate = new Date().format( 'MM/dd/yyyy' )
					billFromDate = billFromDate.format( 'MM/dd/yyyy' )
					queryMap = "billFromDate==$billFromDate;billToDate==$todaysDate"
					logger.info("queryMap......." + queryMap)
				}
			}
		}
		def uri
		if(queryMap.length() > 0) {
			if(roleType != null && roleType.equalsIgnoreCase("Employee")){
				logger.info("calling the spi for EMPLOYEE bill history details...")
				uri="${spiPrefix}/groups/employees/$groupNumber/billProfiles?q=${queryMap};view==history"
			}else{
				logger.info("calling the spi for Employer/Broker bill history details ...")
				uri="${spiPrefix}/groups/$groupNumber/billProfiles?q=${queryMap};view==history"
				logger.info "uri is.."+uri
			}
		}else {
			throw new GSSPException("30004")
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			logger.info "response before is.."
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:], spiHeadersMap)
			logger.info "responseData is.."+responseData
			response = responseData.getBody().items
		} catch (e) {
			logger.info "ERROR while retrieving billing history details from SPI....."+e.toString()
		}
		response
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
	def callBillingHistoryDetailedViewService(registeredServiceInvoker, viewSrvVip, billingDetails, tenantId, groupNumber){

		def viewServiceURI = "/v1/tenants/$tenantId/views/groups/$groupNumber/billings"
		def response = registeredServiceInvoker.post(viewSrvVip, viewServiceURI ,new HttpEntity(billingDetails), Map)
		response
	}

	def getUserType(groupNumber,entityService,tenantId){
		def response
		def userType
		def data = []
		try{
			userType = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_PROFILE, groupNumber, data)
			response = userType.userType
		}catch(Exception e){
			logger.error("Unable to get user type:"+e.getMessage())
			response = ''
		}
		response
	}

	def getRequiredHeaders(List headersList, Map headerMap) {
		logger.info "Configuring spi hearder"
		headerMap<<[(BillingConstants.X_GSSP_TRACE_ID):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {
				spiHeaders << [(header): [headerMap[header]]]
			}
		}
		spiHeaders
	}

	def getYears(date){
		DateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		def year = sdf.parse(date).getAt(Calendar.YEAR)
		year
	}

	def callVoidedActualDetails(WorkflowDomain workFlow){
		def registeredServiceInvoker = workFlow.getBeanFromContext('registeredServiceInvoker', RegisteredServiceInvoker)
		def spiPrefix = workFlow.getEnvPropertyFromContext('spi.prefix')
		def spiMockURI = workFlow.getEnvPropertyFromContext('eipservice.ribbon.listOfServers')
		def config = workFlow.getBeanFromContext("GSSPConfiguration", GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def groupNumber = requestPathParamsMap['groupId']
		def requestParamsMap = workFlow.getRequestParams()
		def billNumber
		def versionNumber
		if (requestParamsMap.get('q')!=null) {
			def requestParam = requestParamsMap.get('q')
			def (key, value) = requestParam.tokenize(";").each{queryParam ->
				def (key, value) = queryParam.tokenize( '==' )
				if (key != null && key == "billNumber") {
					billNumber = value
				}
				if (key != null && key == "versionNumber") {
					versionNumber = value
				}
			}
		}
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext('gssp.headers')
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext('smdgssp.tenantid'),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext('smdgssp.serviceid')]
		def spiHeadersMap = getRequiredHeaders(headersList.tokenize(',') , requestHeaders)
		def resp = getSelfAdminDetailsSPI(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber, billNumber, versionNumber, spiHeadersMap)
		if (resp == null) {
			throw new GSSPException("20012")
		}
		def billMethodCode
		def finalResposne =[:] as Map
		billMethodCode = resp.extension.billMethodCode
		resp?.extension << ['billMethodCode':getBillingMethod(billMethodCode, config,'ref_billing_form_data')]
		resp?.extension << ['billStatusTypeCode':getBillingMethod(resp.extension.billStatusTypeCode, config,'ref_billstatustypecode')]
		resp << ['billMode':getBillingMethod(resp.billMode, config,'ref_billing_form_data')]
		resp?.extension << ['submissionStatusTypeCode':getBillingMethod(resp.extension.submissionStatusTypeCode, config,'ref_billing_form_data')]
		def prod = resp?.getAt('extension')?.getAt('productDetails')
		prod.each({product ->
			def typeCode = product.product.typeCode
			def receivable = product?.getAt('receivable')
			def receivableCode
			receivable.each({recieve->
				def tierCode

				tierCode = recieve?.getAt('tierCode')
				receivableCode = recieve?.getAt('receivableCode')
				if (tierCode != null){
					recieve << ['tierCode':getBillingMethod(tierCode, config, 'ref_self_admin_details')]
				}
			})
			if (receivableCode != null) {
				product.product << ['receivableCode': receivableCode]
				product.product << ['receivableCodeName': getBillingMethod(receivableCode, config, 'ref_self_admin_details')]
			}
			if (!typeCode.isEmpty()) {
				product.product << ['typeCode' : getBillingMethod(typeCode, config, 'ref_self_admin_details')]
			}
		})

		resp.remove('_id')
		resp.remove('self')
		resp.remove('isSubmittedActuals')
		resp.remove('updatedAt')
		finalResposne << ['Response': resp]

		def data = finalResposne.getAt('Response')

		data
	}

	def getBillingMethod(code , config, configurationId){
		code = code.toString()
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		billMethodMap = statusMapping?.data
		billMethodMap[code]
	}

	def getSelfAdminDetailsSPI(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber, billNumber, versionNumber, spiHeadersMap) {

		try {
			def response
			def uri
			if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
				uri= "${spiPrefix}/selfAdminBillProfile"
			} else {
				uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=billNumber==$billNumber;versionNumber==$versionNumber"
			}
			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			def serviceUri = uriBuilder.build(false).toString()
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:],spiHeadersMap)
			if (response != null) {
				logger.info("Selfadmin SPI response for specific version: "+response)
				return response.getBody().item
			}
			response
		} catch (Exception e) {
			logger.error 'Exception in getting SPI for selfAdminVoidedActual ' + e.toString()
			throw new GSSPException("20012")
		}
	}
}

