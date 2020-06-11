package groovy.US

import static java.util.UUID.randomUUID

import java.text.DecimalFormat

import org.springframework.data.mongodb.core.query.Criteria
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

import cz.jirutka.rsql.parser.RSQLParser
import groovy.UTILS.BillingConstants
import groovy.UTILS.DownloadUtil
import groovy.UTILS.ValidationUtil
import groovy.json.JsonOutput
import groovy.time.TimeCategory

class GET_LIST_BILL_DETAILS implements Task {
	def static final EVENT_TYPE_GROUP = 'groupInfo'
	def static final CHANNEL_ID = 'GSSPUI'
	Logger logger = LoggerFactory.getLogger(GET_LIST_BILL_DETAILS.class)
	@Override
	Object execute(WorkflowDomain workFlow) {
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def requestHeaders = workFlow.getRequestHeader()
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID)]
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def requestParamsMap = workFlow.getRequestParams()
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]

		ValidationUtil validation = new ValidationUtil()
		 def validationList = [] as List
		 validationList.add(groupNumber)
		 validation.validateUser(workFlow, validationList)
		 
		def queryParamsMap = workFlow.getRequestParams()?.get('q')
		def rsqlParser = workFlow.getBeanFromContext("rsqlParser", RSQLParser)
		def roleType
		def rsqlMap
		try {
			rsqlMap = DownloadUtil.getQueryMapData(queryParamsMap, rsqlParser)
			if (rsqlMap && rsqlMap['persona']) {
				roleType = rsqlMap['persona']
			} else {
				workFlow.addResponseBody(new EntityResult([globalContent:[
						"Billing Application, persona is missing"
					]], true))
				workFlow.haltWorkflowChain()
			}
			logger.info("Persona information: "+roleType)
		}catch(any) {
			logger.error("Error while parsing query params, "+any.getMessage())
			workFlow.addResponseBody(new EntityResult([globalContent:[
					"Exception while parsing query parameter(s)"
				]], true))
			workFlow.haltWorkflowChain()
		}
		def startDate
		def endDate
		if(requestParamsMap?.get('q') != null) {

			def reqParam = requestParamsMap?.get('q')
			def reqparams = reqParam.tokenize(';')
			for (queryParam in reqparams) {
				if(queryParam.contains('startDate')){
					def value = queryParam.split("==")
					startDate = value[1]
				} else if(queryParam.contains('endDate')) {
					def value = queryParam.split("==")
					endDate = value[1]
				}
			}
		}
		def dueValue = 0.0d
		def billAmt = 0.0d
		//def response = retrieveBillProfileGroupFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI)
		def subGroupResponse = []
		subGroupResponse[0] = getSubGroupdetails(entityService, groupNumber, roleType)?.item
		logger.info("subGroupResponse"+subGroupResponse)
		subGroupResponse[0].each({subGroupsDetail->
			dueValue = subGroupsDetail.dueAmount.amount.toDouble()
			billAmt = subGroupsDetail.billAmount.amount.toDouble()
			//dueValue = (subGroupsDetail.billAmount.amount).toDouble() + (subGroupsDetail.extension.outstandingAmount.amount).toDouble() - (subGroupsDetail.extension.paymentReceivedAmount.amount).toDouble()+ (subGroupsDetail.extension.otherAdjustmentsAmount.amount).toDouble()
		})


		def templateConfigServiceVip = workFlow.getEnvPropertyFromContext('billProfileService.templateConfigServiceVip')
		def prodType=workFlow.getEnvPropertyFromContext('template.prodType')
		def templateName=workFlow.getEnvPropertyFromContext('template.billProfileListBillTemplateName')
		def billProfileMap =[:]
		billProfileMap << ['billProfiles':subGroupResponse[0]]
		logger.debug("billProfileMap"+billProfileMap)
		def hydratedResponse = callTemplateService(registeredServiceInvoker,templateConfigServiceVip,billProfileMap,tenantId,prodType,templateName)?.en_US
		def responseSize = hydratedResponse?.detailedClientData?.items?.size()
		logger.debug("GET_LIST_BILL_DETAILS hydrated response size: "+responseSize)
		def billList=[]
		
		if (hydratedResponse != null && responseSize >0){
			def data = new DecimalFormat("00.00").format(dueValue)
			billAmt = new DecimalFormat("00.00").format(billAmt)
			billList = generateStatementPeriod(startDate, endDate)
			hydratedResponse << ['statementPeriod':billList[0].value]
			hydratedResponse << ['dueAmount': data.toString()]
			logger.info("outside if block, before calling getBillPremiumDetails")
			if (roleType != null && !roleType.equalsIgnoreCase("Employee")) {
				logger.info("Inside if block calling getBillPremiumDetails")
				def billPremiumDetails =  getBillPremiumDetails(entityService, groupNumber, tenantId, billAmt)
				hydratedResponse << ['billPremiumDetails': billPremiumDetails]
			}
			workFlow.addResponseBody(new EntityResult([listBill:hydratedResponse], true))
			workFlow.addResponseStatus(HttpStatus.OK)
		}else{
			hydratedResponse = []
			workFlow.addResponseBody(new EntityResult([listBill:hydratedResponse], true))
			workFlow.addResponseStatus(HttpStatus.OK)
		}
		
	}

	def getBillPremiumDetails(entityService, groupNumber, tenantId, billAmt) {
		logger.info("inside getBillPremiumDetails------------")
		def response = entityService.findById(tenantId, BillingConstants.COLLECTION_NAME_PREMIUM_DETAILS, groupNumber, [])?.billDetails
		def products = response?.productDetails
		logger.info"product details -------->"+ products

		def productCount = response?.productCount
		def productName = [:] as Map
		def product = [] as List
		def prodList =  [] as List
		def respMap = [:]as Map
		def respList = [] as Set
		def prod
		products.each({details ->
			HashMap<String,String> respDetilMap = new HashMap<String,String>()
			prod = details.item.productName

			if (!prod.equals('Bill Fee') && !prod.equals('NSF Fee') && !prod.equals('State Fee')) {
				def premium = details.item.totalProductPremium
				def percentage = ((premium.toFloat()/billAmt.toFloat())*100)
				def dataCeild = Math.round(percentage).toInteger()
				def perc = new DecimalFormat("#.00").format(percentage)
				def data = details.item
				data.putAt('premiumPercentage', dataCeild.toString())
				respMap << ['productDetails' : data]
				logger.info"respMap -------->"+ respMap
				respDetilMap.put('item', respMap?.productDetails)
				respList.add(respDetilMap)
				logger.info"respList -------->"+ respList
				HashMap respProdMap = new HashMap()
				respProdMap.put('productName', prod)
				logger.info"respProdMap -------->"+ respProdMap
				product.add(respProdMap)
			}

		})


		productName << ['productDetails': respList]
		logger.info"productDetails -------->"+ respList

		productName << ['productCount': productCount]
		logger.info"productCount -------->"+ productCount

		productName << ['product': product]
		logger.info"product -------->"+ product
		return productName
	}


	/*
	 * Used to get bill profile details from SPI
	 */
	def retrieveBillProfileGroupFromSPI(registeredServiceInvoker, spiPrefix, groupNumber,spiMockURI, spiHeadersMap) {
		def uri
		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billProfiles?q=accountNumber==$groupNumber&billFromDate==2016-01-01&billToDate==2019-04-01&view==current"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=view==current"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:], spiHeadersMap)

		} catch (Exception e) {
			logger.info "ERROR while retrieving bill profile details from SPI....."+e.toString()
		}
		response.getBody().items.item
	}


	/**
	 * Used to retrieve the subGroup views from subGroupDetails collection in DB
	 *
	 * @param entityService
	 * @param groupNumber
	 *
	 * @return updatedData
	 */
	def getSubGroupdetails(entityService, groupNumber, roleType) {
		def enrollmentsHydratedList
		try{
			Criteria inCriteria
			if (roleType != null && roleType.equalsIgnoreCase("Employee")) {
				inCriteria = Criteria.where("_id").is(groupNumber)
				enrollmentsHydratedList = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_EMPLOYEE_BILL_PROFILE, inCriteria).data[0]
			} else {
				inCriteria = Criteria.where("data.item.accountNumber").is(groupNumber)
				enrollmentsHydratedList = entityService.listByCriteria(BillingConstants.COLLECTION_NAME_BILL_PROFILE, inCriteria).data[0]
			}

			for ( arr in enrollmentsHydratedList) {
				if (arr.item.accountNumber == groupNumber) {
					enrollmentsHydratedList = []
					enrollmentsHydratedList[0] = arr
					break
				}
			}
		}catch(e){
			logger.error('Error getting details ' +"${e.message}")
		}
		enrollmentsHydratedList
	}


	def callTemplateService(registeredServiceInvoker, templateConfigServiceVip, requestData, tenantId, prodCode,typeVal) {
		logger.info "Invoking template config service"
		def response
		def templateConfigServiceUri = "/v1/tenants/${tenantId}/templates"
		try{
			def requestToTemplate = [type: typeVal, prodCode:prodCode, data :requestData]
			def responseTemplate = registeredServiceInvoker.post(templateConfigServiceVip, templateConfigServiceUri, new HttpEntity(requestToTemplate), Map.class)
			response = responseTemplate?.getBody()
			logger.debug("GET_LIST_BILL_DETAILS callTemplateService-Hydrated response :"+response)
		}catch(e){
			logger.error("Error while invoking template config service",e)
		}
		return response
	}

	/**
	 * Used to generate the statement period
	 */

	def generateStatementPeriod(startDate, endDate){
		def billList = []
		def acceptedFormat = "MM/dd/yyyy"
		def stDate = new Date().parse("MM/dd/yyyy", startDate)
		def edDate = new Date().parse("MM/dd/yyyy", endDate)
		def periodCount = [:]
		def period = [:]
		def key = 0
		def value
		periodCount.put("key", "0")
		def startData = (stDate).format(acceptedFormat).concat(' - ').concat((edDate).format(acceptedFormat))
		value = startData
		period << ['key' : key]
		period << ['value':value]
		billList.add(period)
		use(TimeCategory) {
			for(int i=1;i<6;i++){
				def periodMap = [:]
				def prevMonth=(stDate -1.month)
				def finalres = (prevMonth).format(acceptedFormat).concat(' - ').concat((stDate-1.day).format(acceptedFormat))
				key = i
				value = finalres
				periodMap << ['key' : key]
				periodMap << ['value':value]
				billList[i]=periodMap
				stDate=prevMonth
			}

		}
		return billList
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

}

