package groovy.US

import java.util.concurrent.TimeUnit

import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.http.HttpStatus

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import com.metlife.service.entity.EntityService

import groovy.UTILS.BillingConstants

class GET_USER_TYPE implements Task{
	def static final COLLECTION_NAME = "profile"

	Logger logger = LoggerFactory.getLogger(GET_USER_TYPE.class)
	Object execute(WorkflowDomain workFlow) {
		def RT_1 = System.nanoTime();
		def entityService = workFlow.getBeanFromContext("GSSPEntityService", EntityService)
		def userType = workFlow.getRequestBody().get("userType")
		def id = workFlow.getRequestBody().get("number")
		workFlow.addFacts("userType",userType)
		workFlow.addFacts("number",id)

		def recipientTypeCode

		if(userType.equalsIgnoreCase("Broker")) {
			recipientTypeCode = "500"
		} else if(userType.equalsIgnoreCase("TPA")) {
			recipientTypeCode = "501"
		} else if(userType.equalsIgnoreCase("Employer")) {
			recipientTypeCode = "502"
		} else {
			recipientTypeCode = "504"
		}
		workFlow.addFacts("recipientTypeCode",recipientTypeCode)
		def billProfile = getbillProfileForRecipient(entityService, userType, id, recipientTypeCode)

		if(!billProfile){
			throw new GSSPException("40005")
		}
		def RT_4 = System.nanoTime();
		logger.info("Time taken to get final response of Recipient :"+TimeUnit.NANOSECONDS.toMillis(RT_4-RT_1)+" ms")
		workFlow.addResponseBody(new EntityResult([profile:billProfile], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	def  getbillProfileForRecipient(entityService, userType, number, recipientTypeCode){
		def response
		try {
			def RT_2 = System.nanoTime();
			entityService.create(BillingConstants.COLLECTION_NAME_PROFILE,['groupId':number,'userType':userType,'recipientTypeCode':recipientTypeCode])
			def RT_3 = System.nanoTime();
			logger.info("Time taken to get Recipient from DB: "+TimeUnit.NANOSECONDS.toMillis(RT_3-RT_2)+" ms")
			response = userType
		}catch(Exception e) {
			logger.info "Exception in getbillProfile.."+e.toString()
		}
		response
	}
}