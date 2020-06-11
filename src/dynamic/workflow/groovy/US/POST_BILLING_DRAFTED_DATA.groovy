package groovy.US

import org.springframework.data.mongodb.core.query.Criteria

/**
 * This groovy is used to post the drafted bill data
 *
 */

import org.springframework.http.HttpStatus

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.repo.GSSPRepository
import com.metlife.gssp.taskflow.Task
import com.metlife.service.entity.EntityService
import com.metlife.service.entity.GSSPEntityService
import com.metlife.utility.TenantContext

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil

/**
 * 
 * @author panchanan
 *
 */

class POST_BILLING_DRAFTED_DATA implements Task{
	private static final Logger LOGGER = LoggerFactory.getLogger(POST_BILLING_DRAFTED_DATA)
	private static final COLLECTION_NAME = "selfAdminDraft";
	private static billMethodMap = [:]
	@Override
	Object execute(WorkflowDomain workFlow) {
		def entityService = workFlow.getBeanFromContext(BillingConstants.GSSP_ENTITY_SERVICE, EntityService)
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def requestBodyMap = workFlow.getRequestBody()
		def draftedBillData
		draftedBillData = requestBodyMap
		if (draftedBillData == null) {
			throw new GSSPException("30002")
		}
		def groupNumber = requestBodyMap['accountNumber']
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
		def billNumber = requestBodyMap['number']
		draftedBillData.extension << ['billMethodCode' : getBillingMethod(draftedBillData?.extension.billMethodCode,config,'ref_billing_form_data')]
		draftedBillData.extension << ['billStatusTypeCode' : getBillingMethod(draftedBillData?.extension.billStatusTypeCode,config,'ref_billing_form_data')]
		draftedBillData << ['billMode' : getBillingMethod(draftedBillData?.billMode,config,'ref_billing_form_data')]
		draftedBillData.extension << ['submissionStatusTypeCode' : getBillingMethod(draftedBillData?.extension.submissionStatusTypeCode,config,'ref_billing_form_data')]

		for(def productDetails : draftedBillData.extension.productDetails) {
			def typeCode = productDetails?.product?.typeCode
			// Removing receivableCode and its name from product object and persisting in DB
			def rCodeName = productDetails.product.receivableCodeName
			productDetails.product.remove('receivableCode')
			productDetails.product.remove('receivableCodeName')

			for(def receivable : productDetails.receivable){
				def tierCode = receivable?.tierCode
				if (tierCode != null && !tierCode.isEmpty()) {
					receivable << ['tierCode' : getBillingMethod(tierCode,config,'ref_self_admin_details')]
				}
				receivable << ['receivableCode' : getBillingMethod(rCodeName,config,'ref_self_admin_details')]
			}
			if(typeCode != null && !typeCode.isEmpty()) {
				productDetails.product << ['typeCode': getBillingMethod(typeCode,config,'ref_self_admin_details')]
			}
		}

		updateResource( entityService, draftedBillData, tenantId, groupNumber, billNumber)
		workFlow.addResponseBody(new EntityResult([draftedBillData:draftedBillData], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	protected updateResource(GSSPEntityService entityService,draftedBillData,tenantId, groupNumber, billNumber){
		try{
			draftedBillData << ['isSubmittedActuals' : 'false']
			def data
			def searchQuery = ['_id': billNumber,'data.item.accountNumber': groupNumber]
			Criteria inCriteria = Criteria.where("_id").is(billNumber)
			TenantContext.setTenantId(tenantId)
			def result = entityService.listByCriteria(COLLECTION_NAME, inCriteria)
			if (!result.isEmpty()) {
				data = ['data.item' : draftedBillData]
				entityService.updateByQuery(COLLECTION_NAME, searchQuery, data)
			} else {
				data = ['item' : draftedBillData]
				def recordToInsert = ['data': data]
				recordToInsert << ['_id' : billNumber]

				entityService.create(COLLECTION_NAME, recordToInsert)
			}
			TenantContext.cleanup()
		} catch (Exception e) {
			LOGGER.error('Error saving drafted bill data ' +"${e.message}")
			throw new GSSPException("20004")
		}
	}

	def getBillingMethod(code , config,  configurationId){
		code = code.toString()
		if(billMethodMap[code]){
			billMethodMap[code]
		}else {
			def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
			billMethodMap = statusMapping?.data
			billMethodMap[code]
		}
	}
}
