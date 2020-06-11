package groovy.US
import static java.util.UUID.randomUUID
import java.text.SimpleDateFormat
import org.springframework.core.io.Resource
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder
import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.common.excel.ExcelGeneration
import com.metlife.gssp.common.excel.impl.ExcelGenerationImpl
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import com.metlife.service.TokenManagementService
import com.metlife.service.entity.EntityService
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException
import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.RetrieveScanStatus
import groovy.UTILS.ValidationUtil
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class DOWNLOAD_LIST_BILL_DETAILS implements Task{
	private static final Logger LOGGER = LoggerFactory.getLogger(DOWNLOAD_LIST_BILL_DETAILS)
	def static final EVENT_TYPE_PREMIUM_DETAILS = 'PREMIUMDETAILS'
	def static final CHANNEL_ID = 'GSSPUI'

	@Override
	Object execute(WorkflowDomain workFlow) {
		def GET_DASHBOARD_DETAILS dashboardDetails = new GET_DASHBOARD_DETAILS()
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def id = requestPathParamsMap[BillingConstants.GROUP_ID]
		def viewSrvVip = workFlow.getEnvPropertyFromContext(BillingConstants.BILL_PROFILE_VIEW_SERVICE_VIP)
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestParamsMap = workFlow.getRequestParams()
		def response = [:] as Map
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeadersForGetCall(headersList?.tokenize(BillingConstants.COMMA) , requestHeaders)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		boolean isEmployee = false
		if(id == null){
			def employeId = requestPathParamsMap[BillingConstants.ID]
			isEmployee = true
			id = employeId;
		}

		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(id)
		validation.validateUser(workFlow, validationList)

		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_MOCK_URI)
		//DMF
		Map<String, Object> requestParamMap = workFlow.getRequestParams()
		def documentIdSMD
		def fromDate
		def toDate
		def format
		def billNumber
		def mode
		if(requestParamsMap?.get('q') != null) {
			requestParamsMap?.get('q')?.tokenize(';').each{queryParam ->
				def (key, value) = queryParam.tokenize( '==' )
				if (key != null && key == 'documentId') {
					documentIdSMD = value
				}
				if (key != null && key == 'fromDate') {
					fromDate = value
				}
				if (key != null && key == 'toDate') {
					toDate = value
				}
				if (key != null && key == 'format') {
					format = value
				}
				if (key != null && key == 'billNumber') {
					billNumber = value
				}
				if (key != null && key == 'mode') {
					mode = value
				}
			}
		}

		Date currentDate = new Date()
		if(mode == "deduction") {

			if(fromDate != null && toDate != null & format != null) {
				if(format == "0") {
					Date compareFromDate = new Date().parse("MM/dd/yyyy",fromDate)
					LOGGER.info("Comapre deduction FromDate------>" + compareFromDate)
					if((compareFromDate.after(currentDate))) {
						LOGGER.info("deduction current bill creation --->")
						currentBillExcelCreation(entityService, id, isEmployee, workFlow)
					}else {
						def billDetails = getDataFromBillHistory(fromDate, toDate, entityService, isEmployee, id, billNumber)
						LOGGER.info("deduction billDetails-----------------------------:" + billDetails)
						historyBillExcelCreation(registeredServiceInvoker, spiPrefix, id, spiMockURI, billNumber, workFlow,config,viewSrvVip,spiHeadersMap,isEmployee,billDetails,billDetails?.getAt("item"))
					}
				}else {
					def billDetails = getDataFromBillHistory(fromDate, toDate, entityService, isEmployee, id,billNumber)
					documentIdSMD = billDetails?.item?.extension?.documentMetaData[0]?.documentId
					if(documentIdSMD) {
						getDMFDocument(workFlow, spiMockURI,documentIdSMD)
					}else {
						def errorResp = [:]
						errorResp << [
							'code' : "400",
							'message' : "Sorry! We're unable to process your request. Please try again later"
						]
						workFlow.addResponseBody(new EntityResult(errorResp, true))
					}

				}

			}
		}else if(mode == "history") {
			if(billNumber != null && format != null) {
				if(format == "0") {
					def billDetails = getDataFromBillHistory(fromDate, toDate, entityService, isEmployee, id, billNumber)
					LOGGER.info("History billDetails-----------------------------:" + billDetails)
					billNumber = billDetails.getAt("number")
					Date billFromDate = new Date().parse("MM/dd/yyyy", billDetails.getAt("billFromDate"))
					LOGGER.info("History mode billFromDate --->")
					if(billFromDate.after(currentDate)){
						LOGGER.info("History mode current bill details-->")
						currentBillExcelCreation(entityService, id, isEmployee, workFlow)
					}else{
						LOGGER.info("History mode history bill details-->")
						historyBillExcelCreation(registeredServiceInvoker, spiPrefix, id, spiMockURI, billNumber, workFlow,config,viewSrvVip,spiHeadersMap,isEmployee, billDetails, null)
					}
				}
				else if(format == "1"){
					if(documentIdSMD) {
						getDMFDocument(workFlow, spiMockURI,documentIdSMD)
					}else {
						def errorResp = [:]
						errorResp << [
							'code' : "400",
							'message' : "Sorry! We're unable to process your request. Please try again later"
						]
						workFlow.addResponseBody(new EntityResult(errorResp, true))
					}
				}
			}
		}else {
			if(format == "0") {
				currentBillExcelCreation(entityService, id, isEmployee, workFlow)
			}else {
				if((documentIdSMD != null || documentIdSMD != 'null') && format == "1" ) {
					getDMFDocument(workFlow, spiMockURI,documentIdSMD)
				}
			}
		}

		workFlow.addResponseStatus(HttpStatus.OK)
	}
	/**
	 *
	 * @param registeredServiceInvoker
	 * @param spiPrefix
	 * @param groupNumber
	 * @param spiMockURI
	 * @param spiHeadersMap
	 * @param documentIdSMD
	 * @return
	 */
	def searchDocumentFromDMF(registeredServiceInvoker, spiPrefix, spiMockURI, spiHeadersMap, documentIdSMD) {
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/searches/documents"
		} else {
			uri = "${spiPrefix}/searches/documents"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info ("serviceUri == " + serviceUri)
		def response
		def requestBody
		try {
			def reqBody = [:]
			def searchKey = "((searchKeyParameterName==\"documentID\"; searchKeyParameterValue==\"${documentIdSMD}\"))"
			LOGGER.info "Search Key == " + searchKey
			def extension = [:]
			extension << ['sourceSystemName' : 'SMD']
			extension << ['searchTypeCode' : 'MGI']
			def metadata = [:]
			metadata << ['limit' : 100]
			metadata << ['offset' : 1]
			reqBody << ['q' : searchKey]
			reqBody << ['extension' : extension]
			reqBody << ['metadata' : metadata]
			requestBody = JsonOutput.toJson(reqBody)
			LOGGER.info ("Search requestBody == " + requestBody)
			HttpEntity<String> request = registeredServiceInvoker.createRequest(requestBody,spiHeadersMap)
			def responseData = registeredServiceInvoker.postViaSPI(serviceUri, request, Map.class)
			LOGGER.info ("Response Data ================== " + responseData)
			def statusCode = ""+responseData?.statusCode
			if (statusCode == 200 || statusCode == '200' || statusCode.equals('200')) {
				LOGGER.info ("statusCode ================== " + statusCode)
				response = responseData?.getBody()
				LOGGER.info ("final response ================== " + response)
				return response
			}
		} catch (Exception e) {
			e.printStackTrace()
			LOGGER.info("Error while searching document in DMF system"+e.getMessage())
		}
		response
	}
	/**
	 *
	 * @param spiPrefix
	 * @param spiMockURI
	 * @param registeredServiceInvoker
	 * @param header
	 * @param documentId
	 * @return
	 */
	def getListBillPDF(spiPrefix,spiMockURI, registeredServiceInvoker, getSpiHeadersMap, documentId) {
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/documents?q=documentId==${documentId}"
		} else {
			uri = "${spiPrefix}/documents/${documentId}"
		}
		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(uri)
		uriBuilder.queryParam("SourceSystemName", "EAI1234")
		uriBuilder.queryParam("SearchTypeCode", "SMD")
		def serviceUri = uriBuilder.build(false).toString()
		LOGGER.info("final url: "+serviceUri)
		def response
		InputStream responseInputStream
		try {
			ResponseEntity<Resource> responseEntity = registeredServiceInvoker.getViaSPI(serviceUri, Resource.class, [:], getSpiHeadersMap)
			LOGGER.info "responseEntity from DMF....." + responseEntity
			def statusCode = ""+responseEntity?.statusCode
			if (statusCode == 200 || statusCode == '200' || statusCode.equals('200')) {
				responseInputStream = responseEntity.getBody().getInputStream()
				LOGGER.info "responseInputStream - getBody()....." + responseEntity.getBody()
				LOGGER.info "responseInputStream....." + responseInputStream
				return responseInputStream
			}
		} catch (Exception e) {
			LOGGER.info("error while retreiving document from DMF: "+e.getMessage())
		}
		responseInputStream
	}
	/**
	 * Used to retrieve the subGroup views from subGroupDetails collection in DB
	 *
	 * @param entityService
	 * @param groupNumber
	 *
	 * @return billingHostory
	 */
	def getPremiumDetailsFromDB(entityService, id,isEmployee) {
		def response
		Criteria inCriteria = Criteria.where("_id").is(id)
		try {
			if(isEmployee){
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_EMPLOYEE_PREMIUM_DETAILS,inCriteria).view_PREMIUMDETAILS.coverageDetails.coverageDetailsRowData[0]
			}
			else{
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, inCriteria).view_PREMIUMDETAILS.premiumDetails.premiumDetailsRowData
			}
		} catch (e) {
			LOGGER.error('Error in  getPremiumDetails' +"${e.message}")
		}
		response
	}
	def getDeductionDate(entityService, id) {
		def response
		Criteria inCriteria = Criteria.where("_id").is(id)
		try {
			response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS,inCriteria)?.deductionDetails?.items
		} catch (e) {
			LOGGER.error('Error in  getDeductionDetails' +"${e.message}")
		}
		response
	}
	def getGroupDetails(entityService, id,isEmployee){
		def responseData
		def response
		try {
			Criteria inCriteria = Criteria.where("_id").is(id)
			if(isEmployee){
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_PROFILE, inCriteria).data[0].item
			}
			else{
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, inCriteria).data.item
			}
			responseData = response[0]
		} catch (e) {
			LOGGER.error('Error in  getGroupDetails' +"${e.message}")
		}
		responseData
	}
	def getGroupDetailsForFeeAmount(entityService, id,isEmployee){
		def response
		try {
			Criteria inCriteria = Criteria.where("_id").is(id)
			if(isEmployee){
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_PROFILE, inCriteria).data.item.extension
			}
			else{
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_PROFILE_SUBGROUP, inCriteria).data.item.extension
			}
		} catch (e) {
			LOGGER.error('Error in  getGroupDetails' +"${e.message}")
		}
		response[0]

	}
	def getProductDetails(entityService, id) {
		def response
		Criteria inCriteria = Criteria.where("_id").is(id)
		try {
			response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS,inCriteria)?.billDetails
		} catch (e) {
			LOGGER.error('Error in  getProductDetails' +"${e.message}")
		}
		response
	}
	def getAdjustmentDetails(entityService, id){
		def response
		try {
			Criteria inCriteria = Criteria.where("_id").is(id)
			response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, inCriteria)?.view_PREMIUMDETAILS.premiumAdjustmentDetails.premiumAdjustmentDetailsRowData
		} catch (e) {
			LOGGER.error('Error in  getGroupDetails' +"${e.message}")
		}
		response
	}
	/**
	 *
	 * @param headersList
	 * @param headerMap
	 * @return
	 */
	def getRequiredHeaders(List headersList, Map headerMap) {
		headerMap<<[('x-gssp-trace-id'):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {
				spiHeaders << [(header): headerMap[header]]
			}
		}
		spiHeaders
	}
	/**
	 *
	 * @param workFlow
	 * @param spiHeadersMap
	 * @return
	 */
	def buildSPIRequestHeaderMap(WorkflowDomain workFlow, spiHeadersMap) {
		TokenManagementService token = workFlow.getBeanFromContext("tokenManagementService", TokenManagementService.class)
		spiHeadersMap << ["Authorization":token.getToken()]
		spiHeadersMap << ["Content-Type" : workFlow.getEnvPropertyFromContext(BillingConstants.APMC_CONTENT_TYPE)]
		spiHeadersMap << ["UserId" : workFlow.getEnvPropertyFromContext(BillingConstants.APMC_USER_ID)]
		spiHeadersMap << ["X-IBM-Client-Id":workFlow.getEnvPropertyFromContext(BillingConstants.APMC_CLIENT_ID)]
		spiHeadersMap << ["RequestTxnId":workFlow.getEnvPropertyFromContext(BillingConstants.APMC_REQUEST_TXN_ID)]
		spiHeadersMap << ["ServiceName":workFlow.getEnvPropertyFromContext(BillingConstants.APMC_SERVICE_NAME)]
		spiHeadersMap << ["UserType":workFlow.getEnvPropertyFromContext(BillingConstants.APMC_USER_TYPE)]
		LOGGER.info("SPI header map.."+spiHeadersMap)
		spiHeadersMap
	}
	/**
	 *
	 * @param headersList
	 * @param headerMap
	 * @return
	 */
	def getRequiredHeadersForGetCall(List headersList, Map headerMap) {
		headerMap<<[('x-gssp-trace-id'):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {
				spiHeaders << [(header): [headerMap[header]]]
			}
		}
		spiHeaders
	}
	/**
	 *
	 * @param workFlow
	 * @param spiHeadersMap
	 * @return
	 */
	def buildSPIRequestHeaderMapForGetCall(WorkflowDomain workFlow, spiHeadersMap) {
		TokenManagementService token = workFlow.getBeanFromContext("tokenManagementService", TokenManagementService.class)
		spiHeadersMap << ["Authorization":[token.getToken()]]
		spiHeadersMap << ["Content-Type" : [workFlow.getEnvPropertyFromContext(BillingConstants.APMC_CONTENT_TYPE)]]
		spiHeadersMap << ["UserId" : [workFlow.getEnvPropertyFromContext(BillingConstants.APMC_USER_ID)]]
		spiHeadersMap << ["X-IBM-Client-Id":[workFlow.getEnvPropertyFromContext(BillingConstants.APMC_CLIENT_ID)]]
		spiHeadersMap << ["RequestTxnId":[workFlow.getEnvPropertyFromContext(BillingConstants.APMC_REQUEST_TXN_ID)]]
		spiHeadersMap << ["ServiceName":[workFlow.getEnvPropertyFromContext(BillingConstants.APMC_SERVICE_NAME)]]
		spiHeadersMap << ["UserType":[workFlow.getEnvPropertyFromContext(BillingConstants.APMC_USER_TYPE)]]
		LOGGER.info("SPI header map.."+spiHeadersMap)
		spiHeadersMap
	}

	def getDMFDocument(workFlow, spiMockURI, documentIdSMD) {

		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.DMF_PREFIX)
		def fileData
		def responseArray = [] as Set
		def responseMap = ['files' : responseArray]
		def finalResponseMap = [:]
		LOGGER.info("Getting Required Headers")
		def headersList =  workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		LOGGER.info("header list: "+headersList)
		def requestHeaders =  workFlow.getRequestHeader()
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.APMC_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.APMC_SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList?.tokenize(",") , requestHeaders)
		spiHeadersMap = buildSPIRequestHeaderMap(workFlow, spiHeadersMap)
		LOGGER.info "Header List: ..----------->"+{spiHeadersMap}
		def searchMetaData = searchDocumentFromDMF(registeredServiceInvoker, spiPrefix, spiMockURI, spiHeadersMap, documentIdSMD)
		LOGGER.info "searchMetaData from Search API....." + searchMetaData
		if(searchMetaData != null) {
			def documentId = searchMetaData.items[0].documentID
			LOGGER.info "documentId from Search API....." + documentId
			def getSpiHeadersMap = getRequiredHeadersForGetCall(headersList.tokenize(",") , requestHeaders)
			getSpiHeadersMap = buildSPIRequestHeaderMapForGetCall(workFlow, getSpiHeadersMap)
			def  finalResponse = getListBillPDF(spiPrefix,spiMockURI, registeredServiceInvoker, getSpiHeadersMap, documentId)
			if(finalResponse != null) {
				def bytesBase64 =  finalResponse.getBytes().encodeBase64().toString()
				DownloadUtil.antiVirusSecurityScanCode(workFlow, bytesBase64)
				fileData = ['content' : bytesBase64]
				fileData.encodingType = BillingConstants.BASE64
				fileData.contentLength = bytesBase64.length()
				fileData.formatCode = 'pdf'
				fileData.name = BillingConstants.LIST_BILL_FILE_NAME_PDF
				responseArray.add(fileData)
				finalResponseMap << ['Details':responseMap]
				finalResponseMap << ['code':'200']
				finalResponseMap << ['message':'']
				workFlow.addResponseBody(new EntityResult(finalResponseMap, true))
			} else {
				def errorResp = [:]
				errorResp << [
					'code' : "400",
					'message' : "Sorry! We're unable to process your request. Please try again later"
				]
				workFlow.addResponseBody(new EntityResult(errorResp, true))
			}
		} else {
			def errorResp = [:]
			errorResp << [
				'code' : "400",
				'message' : "Sorry! We're unable to process your request. Please try again later",
			]
			workFlow.addResponseBody(new EntityResult(errorResp, true))
		}

	}

	def getDataFromBillHistory(def fromDate, def toDate, def entityService, def isEmployee, def id, def billNumber) {

		def response
		def finalResp = [:]
		try {
			Criteria inCriteria = Criteria.where("_id").is(id)
			if(isEmployee){
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_HISTORY, inCriteria).data.billingScheduleDetails[0]
			}
			else{
				//response = new JsonSlurper().parseText(resp).items
				response = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_HISTORY, inCriteria).data.billingScheduleDetails[0]
				LOGGER.info("getDataFromBillHistory response--------------------"+ response)
			}

		} catch (e) {
			LOGGER.error('Error in  getGroupDetails' +"${e.message}")
		}

		if(response) {
			LOGGER.info("before calling filterHistory--------------------"+ response)
			finalResp = filterHistoryData(response, fromDate, toDate, billNumber)
		}

	}

	def filterHistoryData(response, fromDate, toDate, billNumber) {
		def documentId
		def responseData = [:]
		def billDate
		try {
			for (def data : response) {
				if(billNumber != null) {
					if(data.item.number == billNumber) {
						def documt = data?.item?.extension?.documentMetaData[0]?.documentId
						responseData.putAt("documentId", documt)
						responseData.putAt("number", data?.item?.number)
						responseData.putAt("billFromDate", data?.item?.extension?.billFromDate)
						responseData.putAt("billToDate", data?.item?.extension?.billToDate)
						responseData.putAt("billDate", data?.item?.billDate)
					}
				}else {
					println data.item.extension.billFromDate
					println data.item.extension.billToDate
					def insertable = false
					if(data.item.extension.billFromDate == fromDate && data.item.extension.billToDate == toDate) {

						if(billDate == null) {
							billDate = data.item.billDate
							insertable = true

						}else if(billDate < data.item.billDate) {
							billDate = data.item.billDate
							insertable = true
						}
						if(insertable) {
							def documt = data.item.extension?.documentMetaData[0]?.documentId
							LOGGER.info("documtId--------------------"+ documt)
							responseData.putAt("item", data.item)
							insertable = false
						}
					}
				}
			}
		}catch(Exception e){
			LOGGER.error("error while retreiving document from DMF: "+e.getMessage())
		}
		LOGGER.info("final filterHistory--------------------"+ responseData)
		responseData

	}

	def currentBillExcelCreation(entityService, id,isEmployee, workFlow) {
		def response = [:]
		def premiumResponse = getPremiumDetailsFromDB(entityService, id,isEmployee)
		def deductionResponse = getDeductionDate(entityService, id)
		def groupDetailsForFeeAmt =  getGroupDetailsForFeeAmount(entityService, id,isEmployee)
		response << ['premiumDetails' : premiumResponse[0]]
		response << ['groupDetails' : getGroupDetails(entityService, id,isEmployee)]
		if(isEmployee){
			response << ['groupDetailsForFeeAmount' : groupDetailsForFeeAmt[0]]
          LOGGER.info(" employee groupDetailsForFeeAmount response----------------->"+ response)
		}else{
			response << ['groupDetailsForFeeAmount' : groupDetailsForFeeAmt]
          LOGGER.info("employer groupDetailsForFeeAmount response----------------->"+ response)
		}
		response << ['deductionDetails' : deductionResponse]
		response << ['productDetails' : getProductDetails(entityService, id)]
		LOGGER.info("current response----------------->"+ response)
		if(!isEmployee){
			def adjustmentDetails = getAdjustmentDetails(entityService, id)
			response << ['adjustmentDetails' : adjustmentDetails[0]]
		}
		createExcel(response, workFlow, id, isEmployee, entityService)
	}

	def historyBillExcelCreation(registeredServiceInvoker, spiPrefix, id, spiMockURI, billNumber, workFlow, config, viewSrvVip,spiHeadersMap, isEmployee, billDetails, billProfileInfo) {

		def response = [:]
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def groupDetails
		if (billNumber != null) {
			groupDetails = retrieveBillProfileGroupFromSPI(registeredServiceInvoker, spiPrefix, id, spiMockURI, spiHeadersMap, isEmployee, billDetails)
		} else {
			groupDetails = billProfileInfo
			billNumber = groupDetails?.number
		}

		println "groupdetails::"+ JsonOutput.toJson(groupDetails)
		if(isEmployee) {
			def premiumDetails = new GET_EMPLOYEE_DASHBOARD_DETAILS().retrievePremiumDetailsFromSPI(registeredServiceInvoker, spiPrefix, id, spiMockURI, spiHeadersMap, billNumber)
			def eventDataMapPremiumDetails = buildEventDataPremiumDetailsEmployee(premiumDetails, id, 'US', entityService)
			def hydratedResponse = new GET_EMPLOYEE_DASHBOARD_DETAILS().callPremiumDetailsViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPremiumDetails, 'US', id)
			if(hydratedResponse.getBody() != null) {
				LOGGER.info('employee premium details response is 1: '+response)
				hydratedResponse = hydratedResponse?.getBody()?.response
				LOGGER.info('employee premium details response is: '+response)
			}
			println 'employee hydratedResponse::'+hydratedResponse
			def premiumResponse = hydratedResponse.coverageDetails.coverageDetailsRowData[0]
			LOGGER.info("EmployeePremium Hydrated response---" + premiumResponse)
			response << ['premiumDetails' : premiumResponse]
			response << ['groupDetails' : groupDetails]
			response << ['groupDetailsForFeeAmount' : groupDetails?.extension]
			response << ['deductionDetails' : [:]]
			LOGGER.info("EmployeePremium final response---" + response)

		}else {
			def premiumDetails = new GET_DASHBOARD_DETAILS().retrievePremiumDetailsFromSPI(registeredServiceInvoker, spiPrefix, id, spiMockURI, spiHeadersMap, billNumber)
			println "premiumDetails:"+premiumDetails
			def eventDataMapPremiumDetails = buildEventDataPremiumDetails(premiumDetails, id, 'US', config)
			def temp1 = JsonOutput.toJson(eventDataMapPremiumDetails)
			println 'temp1:::'+temp1
			println 'eventDataMapPremiumDetails###'+temp1
			def hydratedResponse = new GET_DASHBOARD_DETAILS().callPremiumDetailsViewService(registeredServiceInvoker, viewSrvVip, eventDataMapPremiumDetails, 'US', id)
			if(hydratedResponse.getBody() != null) {
				LOGGER.info('premium details response is 1: '+response)
				hydratedResponse = hydratedResponse.getBody()?.response
				LOGGER.info('premium details response is: '+response)
			}
			println 'hydratedResponse::'+hydratedResponse
			def premiumResponse = hydratedResponse.premiumDetails.premiumDetailsRowData
			println 'premiumResponse::'+premiumResponse
			def deductionResponse = []
			deductionResponse.add(eventDataMapPremiumDetails?.resourceName_PREMIUMDETAILS?.deductionSchedule?.items)
			println 'deductionResponse::'+deductionResponse

			def productDetails = []
			productDetails.add(eventDataMapPremiumDetails?.resourceName_PREMIUMDETAILS?.billPremiumDetails)
			LOGGER.info('productDetails------------->' + productDetails)

			def temp = JsonOutput.toJson(hydratedResponse)
			println 'temp:::'+temp
			def adjustmentDetails = hydratedResponse.premiumAdjustmentDetails.premiumAdjustmentDetailsRowData

			response << ['adjustmentDetails' : adjustmentDetails]
			response << ['premiumDetails' : premiumResponse]
			response << ['groupDetails' : groupDetails]
			LOGGER.info("reponse after groupDetails-------->" + response)
			response << ['groupDetailsForFeeAmount' : groupDetails?.extension]
			LOGGER.info("reponse after groupDetailsForFeeAmount-------->" + response)
			response << ['deductionDetails' : deductionResponse]
			LOGGER.info('before product response----------------->'+ response)
			response << ['productDetails' : productDetails]
			LOGGER.info('Final response After product----------------->'+ response)

			println 'reponse:'+deductionResponse

		}
		println "final response:"+response

		createExcel(response, workFlow, id, isEmployee,entityService)
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
			response << ["memberAdjustments" : new GET_DASHBOARD_DETAILS().getAdjPremiumDetails(memberPremium)]
			response << ["memberPremiumDetails" : new GET_DASHBOARD_DETAILS().getPremiumDetails(memberPremium)]
			response << ["coverageDetails" : new GET_DASHBOARD_DETAILS().getCoverageDetails(memberPremium)]

			def deduc = new GET_DASHBOARD_DETAILS().getDeductionDetails(memberPremium)
			response << ["deductionSchedule": new GET_DASHBOARD_DETAILS().getFinalDeductionDetails(deduc, config)]
			LOGGER.info "deductionSchedulet final response Data ------------>" + response

			def productBreakdown = new GET_DASHBOARD_DETAILS().getFinalProduct(billPremium, config)
			response << ["billPremiumDetails" : productBreakdown]

			LOGGER.info "billPremiumDetails fianl response---------->" + response

			if (response.memberAdjustments == '' || response.memberAdjustments == null || response.memberAdjustments == 'null' || response.memberAdjustments == "") {
				response << ["noOfAdj" : 0]
			}else{
				response << ["noOfAdj" : response.memberAdjustments.size]
			}
			response << ["numberOfInsured" : memberPremium.item[0].numberOfInsured]
			response << ["numberOfDependents" : memberPremium.item[0].numberOfDependents]
			response << ["store" : 'no']
			eventDataMapGroup << [groupNumber:groupNumber, viewType:BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PREMIUM_DETAILS, tenantId: tenantId,
				resourceName: (resName), (resName):response]
			LOGGER.info "buildEventData Event Data " + eventDataMapGroup
		}
		eventDataMapGroup
	}

	def createExcel(response,workFlow, id, isEmployee, entityService) {
		def file, bytesBase64, fileData
		def responseArray = [] as Set
		def responseMap = ['files' : responseArray]
		def excelOutputPath = workFlow.getEnvPropertyFromContext('templateNewExcelPath') + '/output/'
		File excelFile=new File(excelOutputPath)
		String[] myExcelFiles
		if(excelFile.isDirectory()){
			myExcelFiles = excelFile.list()
			for(files in myExcelFiles) {
				File myFile = new File(excelFile, files)
				myFile.delete()
			}
		}
		String timeStmp = new SimpleDateFormat("yyyyMMddhhmm").format(new Date())
		String excelOutputFileName = "$excelOutputPath$timeStmp"
		def chartName = workFlow.getEnvPropertyFromContext('chartName')
		def activityListExcel = DownloadUtil.listBillDataForExcelOutput(response, excelOutputFileName, chartName, id,isEmployee)
		// Generate excel
		ExcelGeneration excelGeneration = workFlow.getBeanFromContext('excelGenerationImpl', ExcelGenerationImpl.class)
		byte[] xlsBytes
		def deductionResponse = getDeductionDate(entityService, id)
		LOGGER.info('deductionResponse inside createExcel---------------: '+deductionResponse)
		int size = deductionResponse?.size
		LOGGER.info('deduction size------------:'+ size)
		if(isEmployee){
			LOGGER.info("Inside isEmployee ")
			xlsBytes =  excelGeneration.generateExcel(activityListExcel, "listBillEmployee-xls.xls")
		}else{
			if (deductionResponse == null || deductionResponse.size ==0){
				xlsBytes =  excelGeneration.generateExcel(activityListExcel, "listBill-xls.xls")
			}else{
				xlsBytes =  excelGeneration.generateExcel(activityListExcel, "listBillWithPayrollDeduction-xls.xls")
			}
		}
		def excelFilename

		excelFilename = DownloadUtil.createExcelFile(excelOutputFileName, xlsBytes)
		System.out.println("excelFilename "+ excelFilename)
		if (deductionResponse == null || deductionResponse.size == 0){
			excelFilename = DownloadUtil.createExcelFile(excelOutputFileName, xlsBytes)
		}else{
			excelFilename = DownloadUtil.createExcelFileForDeduction(excelOutputFileName, xlsBytes)
		}

		file = new File(excelFilename)
		bytesBase64 = file.getBytes().encodeBase64().toString()
		DownloadUtil.antiVirusSecurityScanCode(workFlow, bytesBase64)
		fileData = ['content' : bytesBase64]
		fileData.encodingType = BillingConstants.BASE64
		fileData.contentLength = bytesBase64.length()
		fileData.formatCode = 'XLS'
		fileData.name = BillingConstants.LIST_BILL_FILE_NAME
		responseArray.add(fileData)
		workFlow.addResponseBody(new EntityResult([Details:responseMap], true))
	}

	def retrieveBillProfileGroupFromSPI(registeredServiceInvoker, spiPrefix,groupNumber,spiMockURI, spiHeadersMap,  isEmployee, billDetails) {
		def uri
		def billDate = billDetails.getAt("billDate")
		def billNum = billDetails.getAt("number")
		if(isEmployee){
			uri = "${spiPrefix}/groups/employees/$groupNumber/billProfiles?q=billFromDate==$billDate;billToDate==$billDate;view==history"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=billFromDate==$billDate;billToDate==$billDate;view==history"
			LOGGER.info("profile group uri----------------------->" + uri)
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			def responseData = registeredServiceInvoker.getViaSPI(serviceUri, Map, [:], spiHeadersMap)
			LOGGER.info("profile group responseData----------------------->" + responseData)
			response = responseData?.getBody()?.items[0]?.item

			LOGGER.info("profile group response----------------------->" + response)
		} catch (Exception e) {
			LOGGER.error "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		response
	}

	/**
	 *
	 * @param resourceContents
	 * @param groupNumber
	 * @param tenantId
	 * @return
	 */
	def buildEventDataPremiumDetailsEmployee(resourceContents, employeeId, tenantId, entityService){
		LOGGER.info "inside buildEventDataPremiumDetails"
		def response = [:] as Map
		def eventDataMapGroup = [:]
		def resName = 'resourceName' + '_' + EVENT_TYPE_PREMIUM_DETAILS
		if(!resourceContents.empty) {
			response << ["metadata" : resourceContents.metadata]
			response << ["coverageDetails" : new GET_EMPLOYEE_DASHBOARD_DETAILS().getCoverageDetails(resourceContents, employeeId, entityService, tenantId)]
			response << ["store" : 'no']
			LOGGER.info("EmployeeEventDataPremium response"+ response)
			eventDataMapGroup << [groupNumber:employeeId, viewType:BillingConstants.COLLECTION_NAME_EMPLOYEE_PREMIUM_DETAILS, accessKey:'', channelId:CHANNEL_ID, eventType:EVENT_TYPE_PREMIUM_DETAILS, tenantId: tenantId,
				resourceName: (resName), (resName):response]
			LOGGER.info "buildEventData Event Data " + eventDataMapGroup
		}
		eventDataMapGroup
	}
}

