package groovy.UTILS

import java.util.Map

import org.springframework.data.mongodb.core.query.Criteria

import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.logging.Logger
import com.metlife.service.entity.EntityService
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.exception.GSSPException
/**
 * This groovy is used for validating the logged in user details
 * It will throw 401 unauthorized if the user is invalid
 *
 * author: vakul
 */


class ValidationUtil {
	private final Logger LOGGER = LoggerFactory.getLogger(ValidationUtil.class)
	
	def boolean validateUser(workFlow, validationList){
		def validationFlag = workFlow?.getEnvPropertyFromContext('securityCheck')
		if(validationFlag?.equalsIgnoreCase("true")){
		try{
			def entityService = workFlow.getBeanFromContext("GSSPEntityService", EntityService)
			def requestHeader = workFlow.getRequestHeader()
			def requestPathParamsMap = workFlow.getRequestPathParams()
			def tenantId = requestPathParamsMap['tenantId']
			def metrefId = requestHeader["x-met-rfr-id"]
			def sessionId =requestHeader["x-session-id"]
			def saltId = requestHeader["x-salt-id"]
			def key = sessionId+'_'+metrefId
			def validateUserData = validateUserDetails(entityService,tenantId, key)
			def flag = false
			validationList?.add(saltId)
			validationList?.each({ validationKey->
				if(validateUserData?.containsKey(validationKey)){
					flag =true
				}else{
					throw new GSSPException('UNAUTHORIZED')
				}
			})
		}catch(Exception ex){
			throw new GSSPException('UNAUTHORIZED')
		}
		}
		
	}
	
	def validateUserDetails(entityService, tenantId, key){
		def resp
		try {
			//resp = entityService.findById(tenantId, "Validate", key, [])
			Criteria criteria = (Criteria.where("_id")).is(key)
			resp =entityService.listByCriteria("Validate", criteria)
			if(resp) {
			       resp = resp[0]
			}
		}catch(e){
			throw new GSSPException('UNAUTHORIZED')
		}
		resp
	}
}