package groovy.UTILS

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import javax.xml.bind.DatatypeConverter

import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.excel.ExcelGeneration
import com.metlife.gssp.common.excel.impl.ExcelGenerationImpl
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import net.minidev.json.JSONObject
import net.minidev.json.parser.JSONParser
import net.minidev.json.parser.ParseException
import org.apache.poi.hssf.usermodel.HSSFCell
import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.hssf.usermodel.HSSFFont
import org.apache.poi.hssf.usermodel.HSSFPalette
import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.aspectj.weaver.ast.Instanceof

import cz.jirutka.rsql.parser.ast.Node
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat

/**
 * This groovy is used for download document in Excel format
 * table data from DB
 *
 * author: vakul
 */

class DownloadUtil {

	/**
	 * This method builds data for excel download
	 *
	 * @param requestBody
	 * @return response body
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(DownloadUtil)
	def buildActivityRequest(WorkflowDomain workFlow) {
		def bytesBase64, fileData
		def excelTemplateFileName = workFlow.getFact('templateFile', String.class)
		def activityListExcel = workFlow.getFact('activityListExcel', Map.class)
		ExcelGeneration excelGeneration = workFlow.getBeanFromContext('excelGenerationImpl', ExcelGenerationImpl.class)
		byte[] xlsBytes =  excelGeneration.generateExcel(activityListExcel, excelTemplateFileName+"-xls.xls")
		def response = DatatypeConverter.printBase64Binary(xlsBytes);
		def responseArray = [] as Set
		fileData = ['content' : response]
		fileData.encodingType = 'BASE64'
		fileData.contentLength = bytesBase64.length()
		fileData.formatCode = 'XLS'
		responseArray.add(fileData)
		return responseArray
	}
	/**
	 * This method builds data for excel download
	 *
	 * @param requestBody param
	 * @return response body
	 */
	def downloadFileRequest(WorkflowDomain workFlow) {
		def bytesBase64, fileData
		def dataRequest = workFlow.getFact(factName, type)
		def responseArray = [] as Set
		def response = DatatypeConverter.printBase64Binary(dataRequest.toString().getBytes());
		fileData = ['content' : response]
		fileData.encodingType = 'BASE64'
		fileData.contentLength = bytesBase64.length()
		fileData.formatCode = 'XLS'
		responseArray.add(fileData)
		return responseArray
	}

	static def premiumDataForExcelOutput(Map<String,Object> activityList, String excelOutputFileName, def chartName,def id){
		def activityListExcel = buildPremiumActivityListExcelData(activityList, chartName, id)
		def fileName = "${excelOutputFileName}.json"
		def activityListJson = new JsonBuilder(activityListExcel).toPrettyString()
		def jsonOutputFile = new File(fileName)
		jsonOutputFile.getParentFile().mkdirs()
		jsonOutputFile.write(activityListJson)
		Map<String, Object> jsonMap = getJsonDataAsMap(fileName)
		jsonMap
	}

	static def listBillDataForExcelOutput(Map<String,Object> activityList, String excelOutputFileName, def chartName,def id, boolean isEmployee){
		def activityListExcel = buildListBillActivityListExcelData(activityList, chartName, id,isEmployee)
		LOGGER.info('activityListExcel------------------------>'+ JsonOutput.toJson(activityListExcel))
		def fileName = "${excelOutputFileName}.json"
		def activityListJson = new JsonBuilder(activityListExcel).toPrettyString()
		def jsonOutputFile = new File(fileName)
		jsonOutputFile.getParentFile().mkdirs()
		jsonOutputFile.write(activityListJson)
		// Get test data for input
		Map<String, Object> jsonMap = getJsonDataAsMap(fileName)
		jsonMap

	}
	static def prepareDataForExcelOutput(List< Object> activityList, String excelOutputFileName, def chartName,def year, def billNumber, def billFromDate, def billToDate) {
		def activityListExcel = buildActivityListExcelData(activityList, chartName, year,billNumber, billFromDate, billToDate)
		def fileName = "${excelOutputFileName}.json"
		def activityListJson = new JsonBuilder(activityListExcel).toPrettyString()
		def jsonOutputFile = new File(fileName)
		jsonOutputFile.getParentFile().mkdirs()
		jsonOutputFile.write(activityListJson)
		Map<String, Object> jsonMap = getJsonDataAsMap(fileName)
		jsonMap
	}

	static def buildListBillActivityListExcelData(Map<String,Object> activityList, def chartName,def id,boolean isEmployee){
		LOGGER.info("inside buildListBillActivityListExcelData------------------ ")
		def outputData = [:]
		def data = [:]
		def activities = [] as List
		def premiumData = [] as List
		def adjustmentData = [] as List
		def deductionData = [] as List
		def productData = [] as List

		outputData.putAt('data', data)
		outputData.putAt('logo', chartName)
		def premiumDetails = activityList['premiumDetails']
		def groupDetails = activityList['groupDetails']
		def adjustmentDetails = activityList['adjustmentDetails']
		def groupDetailsForFeeAmount = activityList['groupDetailsForFeeAmount']
		def deductionDetails= activityList['deductionDetails']
		def productDetails = activityList['productDetails']
		data.putAt('premium', premiumData)
		data.putAt('adjustment', adjustmentData)
		data.putAt('deduction', deductionData)
		data.putAt('product', productData)
		data.putAt('activities', activities)
		data.putAt('billNumber',groupDetails?.number)
		data.putAt('groupNumber',groupDetails?.accountNumber)
		data.putAt('Name',groupDetails.extension.entityName)
		data.putAt('EmployeeID',id)
		data.putAt('dueDate', groupDetails.dueDate)
		//data.putAt("billingPeriod", groupDetails.extension.billFromDate+" - "+groupDetails.extension.billToDate)
		data.putAt('BillingFee','$0.00')
		data.putAt('NonSufficientFundFee','$0.00')
		data.putAt('StateFee','$0.00')
		def feeAmounnts
		if(null!=groupDetailsForFeeAmount){
			feeAmounnts = groupDetailsForFeeAmount.feeAmounts
		}

		def metlifeFee = 0.0
		if(null!= feeAmounnts){
			feeAmounnts.each({feeAmountData ->
				metlifeFee += feeAmountData?.feeAmount?.amount?.toFloat()
			})
			LOGGER.info("metlifeFee----------------->"+ metlifeFee)
			data.putAt("MetlifeFee", checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(metlifeFee))))
		}else{
			data.putAt('MetlifeFee', '$0.00')
		}
		if(isEmployee){

			def activityInfo = buildPremiumListOutputDateEmpl(premiumDetails)
			if (activityInfo) {
				for (def info: activityInfo) {
					activities.add(info)
					LOGGER.info("final response of excel certificate ---->"+ activities)
				}
			}

		}
		else{
			for(def activity : premiumDetails){
				def activityInfo = buildPremiumListOutputDate(activity)
				if (activityInfo) {
					//premiumData.add(activityInfo)
					for(def eachMemberActivity : activityInfo){
						premiumData.add(eachMemberActivity)
					}
				}
			}
			for(def activity : productDetails){
				//def activityInfos = productOutputData(activity)
				def activityInfo =  buildProductOutputData(activity)
				if(activityInfo){
					for(def eachMemberActivity : activityInfo){
						productData.add(eachMemberActivity)
					}
				}
			}
			for(def activity : adjustmentDetails){
				def activityInfo = buildPremiumAdjustmentOutputDate(activity)
				if (activityInfo) {
					for(def eachMemberActivity : activityInfo){
						adjustmentData.add(eachMemberActivity)
					}
				}
			}

			for(def activity : deductionDetails){
				def activityInfos = deductionDetailsOutputData(activity)
				LOGGER.info("deduction first activityInfos" + activityInfos)
				def activityInfo = buildDeductionDetailsOutputData(activityInfos)
				LOGGER.info("deduction second activityInfo" + activityInfo)
				if (activityInfo) {
					deductionData.add(activityInfo)
				}
			}


		}
		data.putAt('TotalPremium',checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(groupDetails?.billAmount.amount))))
		data.putAt('Outstanding',checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(groupDetails?.extension.outstandingAmountExcel?.amount))))
		LOGGER.info("Final data after adding outstandingAmountExcel :"+data)
		data.putAt('Suspense',checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(groupDetails?.extension.suspenseAmount.amount))))
		data.putAt('OtherAdjustment',checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(groupDetails?.extension.otherAdjustmentsAmount.amount))))
		data.putAt('Total',checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(groupDetails?.extension?.dueAmountExcel?.amount))))
		LOGGER.info("Final data after adding dueAmountExcel : "+data)
		return outputData
	}

	static def buildPremiumActivityListExcelData(Map<String,Object> activityList, def chartName,def id){
		def outputData = [:]
		def data = [:]
		def activities = [] as List
		outputData.putAt('data', data)
		outputData.putAt('logo', chartName)
		def premiumDetails = activityList['premiumDetails']
		LOGGER.info(premiumDetails + "premiumDetails")
		def groupDetails = activityList['groupDetails']
		data.putAt('activities', activities)
		data.putAt('BillNumber',groupDetails.billNumber)
		data.putAt('EmployeeName',groupDetails.entityName)
		data.putAt('EmployeeID',id)
		data.putAt("BillingPeriod", groupDetails.billFromDate+"-"+groupDetails.billToDate)
		def premium = []
		premium = premiumDetails
		premium.sort { a,b ->
			a.product.name <=> b.product.name
		}
		Iterator premiumIterator = premium.iterator()
		def nextMemberCertificate
		def memberCertificate
		int count = 0
		while(count <= premium.size()){
			if(nextMemberCertificate == null)
			{
				memberCertificate = premiumIterator.next()
			}
			if(nextMemberCertificate != null)
			{
				memberCertificate = nextMemberCertificate
			}
			if(premiumIterator.hasNext())
			{
				nextMemberCertificate = premiumIterator.next()
			}
			def activityInfo = [:]
			try {
				activityInfo.putAt('Coverage', memberCertificate.product.name)
				activityInfo.putAt('Tier',memberCertificate.tier)
				activityInfo.putAt('Volume',memberCertificate.volume)
				activityInfo.putAt('Adjustment',getAdjustment(memberCertificate.adjustmentReasonCode))
				activityInfo.putAt('AdjustmentDate',memberCertificate.adjustmentDate)
				if((nextMemberCertificate!=null && !((memberCertificate.product.name).equals(nextMemberCertificate.product.name))) || (memberCertificate.equals(nextMemberCertificate)))
				{
					activityInfo.putAt('Premium',checkNegativeSign(memberCertificate?.employeePremiumAmount.amount))
				}
			}
			catch (Exception e) {
				LOGGER.error("Unable to update data: "+e.getMessage())
			}
			if (activityInfo) {
				activities.add(activityInfo)
			}
			count++
		}

		data.putAt('TotalPremium',checkNegativeSign(groupDetails?.billAmount))
		data.putAt('Outstanding',checkNegativeSign(groupDetails?.outstandingAmount))
		data.putAt('MetlifeFee','$'+'0.00')
		data.putAt('Suspense',checkNegativeSign(groupDetails?.suspenseAmount))
		data.putAt('OtherAdjustment',checkNegativeSign(groupDetails?.otherAdjustmentsAmount))
		def total = (groupDetails?.billAmount).toFloat() + (groupDetails?.outstandingAmount).toFloat() + (groupDetails?.suspenseAmount).toFloat() + (groupDetails?.otherAdjustmentsAmount).toFloat()
		data.putAt('Total',checkNegativeSign(new DecimalFormat("#.00").format(total)))
		return outputData
	}

	static def getAdjustment(def adjustmentCode){
		def adjustment
		switch(adjustmentCode){
			case '201':
				adjustment = "New Employee Addition"
				break
			case '202':
				adjustment = "PROCESSING"
				break;
			case '203':
				adjustment = "PARTIALLY PAID"
				break;
		}
	}
	static def buildActivityListExcelData(List<Object> activityList, def chartName,def year, def billNumber, def billFromDate , def billToDate) {
		def outputData = [:]
		def data = [:]
		def activities = [] as List
		outputData.putAt('data', data)
		outputData.putAt('logo', chartName)
		data.putAt('activities', activities)
		LOGGER.info('Download Utils activityList ....' +activityList)

		for(def activity : activityList){
			def activityInfo = buildActivityListOutputData(activity?.item)
			if (activityInfo) {
				activities.add(activityInfo)
			}
			data.putAt('GroupNumber',activity?.item.accountNumber)
			def groupName = activity?.item.extension.entityName
			if(groupName != null){
				data.putAt('GroupName',groupName)
			}else{
				data.putAt('GroupName',"")
			}

		}

		if(billNumber != null){
			outputData.putAt('period', billFromDate +" - "+ billToDate)
		}else if (null != year && !year.equals("\'\'") && !'default'.equals(year)) {
			outputData.putAt('period', "01/01/$year" +" - "+ "12/31/$year")
		}else{
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
			Calendar cal = Calendar.getInstance()
			Date toDayDate = cal.getTime()
			cal.add(Calendar.YEAR, -1)
			cal.add(Calendar.DAY_OF_MONTH, +1)
			Date oneYearBackDate = cal.getTime()
			def fromDate = formatter.format(oneYearBackDate)
			def toDate = formatter.format(toDayDate)
			outputData.putAt('period', fromDate +" - "+ toDate)
		}
		LOGGER.info('Download Utils Mapped outputData ....' +outputData)
		return outputData

	}

	private static def buildPremiumAdjustmentOutputDate(def activity){
		def activityList = [] as List
		if(activity){
			activity?.memberCertificates.each({certificate ->
				def activityInfo = [:]
				activityInfo.putAt('firstName', activity.firstName)
				activityInfo.putAt('lastName', activity.lastName)
				activityInfo.putAt('ID',activity.empID)
				activityInfo.putAt('classAdjustment',activity?.class)
				def total = DownloadUtil.checkNegativeSign(DownloadUtil.decimalFormattingWithComma(DownloadUtil.validateStringAndParseDouble(activity?.total)))
				activityInfo.putAt('TotalPremiumAdjustment', total)
				activityInfo.putAt('Adjustment',certificate?.adjustment)
				activityInfo.putAt('cobra', activity.cobra)
				activityInfo.putAt('department', certificate?.department)
				activityInfo.putAt('AdjustmentDate',certificate?.date)
				activityInfo.putAt('volume',certificate?.volume)
				activityInfo.putAt('Coverage',certificate?.coverage)
				def premium = DownloadUtil.checkNegativeSign(DownloadUtil.decimalFormattingWithComma(DownloadUtil.validateStringAndParseDouble(certificate?.premium)))
				activityInfo.putAt('Premium', premium)
				activityList.add(activityInfo)
			})
		}
		activityList
	}

	private static def buildPremiumListOutputDate(def activity){
		def activityList = [] as List
		if(activity){
			activity.memberCertificates.each({certificate ->
				def activityInfo = [:]
				activityInfo.putAt('coverage', certificate?.coverage)
				activityInfo.putAt('tier', certificate?.tierCode)
				activityInfo.putAt('billMonth', certificate?.billMonth)
				activityInfo.putAt('volume', certificate?.volume)
				def premium = DownloadUtil.checkNegativeSign(DownloadUtil.decimalFormattingWithComma(DownloadUtil.validateStringAndParseDouble(certificate?.premium)))
				activityInfo.putAt('premium', premium)
				activityInfo.putAt('firstName', activity.firstName)
				activityInfo.putAt('lastName', activity.lastName)
				activityInfo.putAt('cobra', activity.cobra)
				activityInfo.putAt('department', certificate.department)
				activityInfo.putAt('empID',activity.empID)
				activityInfo.putAt('className',activity?.class)
				def total = DownloadUtil.checkNegativeSign(DownloadUtil.decimalFormattingWithComma(DownloadUtil.validateStringAndParseDouble(activity?.total)))
				activityInfo.putAt('TotalPremium',total)
				activityList.add(activityInfo)
			})

		}
		activityList
	}

	private static def buildProductOutputData(def activityInfos){
		def activityList = [] as List
		if(activityInfos){
			activityInfos?.productDetails.each({product ->
				def activityInfo = [:]
				activityInfo.putAt('productName', product.item.productName)
				activityInfo.putAt('volume', product.item.volume)
				def premium =  product.item?.totalCoverageAmount
				activityInfo.putAt('totalCoverageAmount', DownloadUtil.checkNegativeSign(DownloadUtil.decimalFormattingWithComma(DownloadUtil.validateStringAndParseDouble(premium))))
				activityInfo.putAt('memberCount', product.item?.memberCount)
				activityList.add(activityInfo)
			})
		}
		activityList
	}

	private static def deductionDetailsOutputData(def activity){

		for (datas in activity) {
			for (data in datas.deductionSchedule) {
				def deductDate = data.deductionDate

				if (deductDate == '' || deductDate == null || deductDate == 'null' || deductDate == ""){
					data<< ["deductionDate" : '-']
					data<< ["deductionAmount" : '-']
					data<< ["coverage" : '-']
				}else{
					data<< ["deductionDate" : deductDate]
				}
			}
			for (data in datas.deductionSchedule) {
				def deductAmt = data.deductionAmount
				if (deductAmt == '' || deductAmt == null || deductAmt == 'null' || deductAmt == ""){
					data<< ["deductionDate" : data?.deductionDate]
					data<< ["deductionAmount" : '0.00']
					data<< ["coverage" : data?.coverage]
				}else{
					data<< ["deductionAmount" : deductAmt]
				}
			}
		}
		activity

	}

	private static def buildDeductionDetailsOutputData(def activity){
		int count = 1
		def memberKeyList = [] as List
		def memberValueList = [] as List
		def memberCoverageList = [] as List
		def finalResponse = [:] as Map


		//deduction first column
		activity.each({data->
			memberKeyList.add(data?.memberName?.lastName +', '+ data?.memberName?.firstName+ '   '+data?.memberId )
			memberKeyList.add('Date:')
			count++
			data?.deductionSchedule.each({schedule->
				memberKeyList.add(schedule?.deductionDate)
			})
			memberKeyList.add(' ')
		})

		//deduction second column
		activity.each({data->
			memberCoverageList.add("@hide")
			memberCoverageList.add('Coverage:')
			data?.deductionSchedule.each({schedule->
				memberCoverageList.add(schedule?.coverage)
			})
			memberCoverageList.add(' ')
		})

		//deduction third column
		activity.each({data->
			//memberValueList.add('Insured ID: ' + data?.memberId)
			memberValueList.add("@hide")
			memberValueList.add('Deduction Amount:')
			data?.deductionSchedule.each({schedule->
				def deducAmt = schedule?.deductionAmount
				if(deducAmt == '-'){
					memberValueList.add(deducAmt)
				}else{
					memberValueList.add(DownloadUtil.checkNegativeSign(DownloadUtil.decimalFormattingWithComma(DownloadUtil.validateStringAndParseDouble(deducAmt))))
				}
			})
			memberValueList.add(' ')
		})
		finalResponse.putAt('memberKey', memberKeyList)
		finalResponse.putAt('memberValue', memberValueList)
		finalResponse.putAt('memberCoverage', memberCoverageList)
		finalResponse
	}

	private static def buildPremiumListOutputDateEmpl(def activity){
		LOGGER.info("direct bill activity--->"+ activity)
		def activityList = [] as List
		if(activity){
			try {
				activity?.memberCertificates.each({memberCertificate ->
					def activityInfo = [:]
					def cls = memberCertificate.getAt('class')
					activityInfo.putAt('FirstName',activity?.firstName)
					activityInfo.putAt('LastName',activity?.lastName)
					activityInfo.putAt('empID',activity?.empID)
					activityInfo.putAt('billMonth',memberCertificate?.billMonth)
					activityInfo.putAt('receivableCode',memberCertificate?.receivableCode)
					def premiumAmt = DownloadUtil.checkNegativeSign(DownloadUtil.decimalFormattingWithComma(DownloadUtil.validateStringAndParseDouble(memberCertificate?.premium)))
					activityInfo.putAt('Premium', premiumAmt)
					activityInfo.putAt('Volume',memberCertificate?.volume)
					activityInfo.putAt('Tier',memberCertificate?.tier)
					activityInfo.putAt('classCode',cls)
					activityInfo.putAt('AdjustmentCodeReason',memberCertificate?.adjustment)

					def date = memberCertificate?.date
					if(date == null || date == '' || date.equals('')){
						activityInfo.putAt('date', memberCertificate?.billMonth)
					}else{
						activityInfo.putAt('date', date)
					}
					activityList.add(activityInfo)
				})
			}
			catch (Exception e) {
				LOGGER.error("Unable to update direct bill data: "+e.getMessage())
			}
		}
		activityList
	}



	/**
	 * Pick required fields for output data, make necessary conversion as needed
	 * @param billingDetail
	 * @return
	 */
	private static def buildActivityListOutputData(def activity) {
		def activityInfo = [:]
		LOGGER.info('Download Utils buildActivityListOutputData ....' +activity)
		if(activity){
			try {
				def billDate = activity?.billDate
				activityInfo.putAt('SendDate',billDate)
				LOGGER.info('Download Utils SendDate ....' +activityInfo)

				def activitybillAmount=activity?.billAmount.amount
				LOGGER.info('Download Utils activitybillAmount ....' +activitybillAmount)

				activityInfo.putAt('CurrentGross',checkNegativeSign(activitybillAmount))
				LOGGER.info('Download Utils CurrentGross ....' +activityInfo)

				def billFrDate = activity?.extension.billFromDate
				def billToDate = activity?.extension.billToDate
				activityInfo.putAt('StatementPeriod', billFrDate +" - "+ billToDate)

				def dueDate = activity?.dueDate
				activityInfo.putAt('DueDate',dueDate)

				activityInfo.putAt('TotalDue',activity != null ? checkNegativeSign(activity.dueAmount.amount) : '')
				activityInfo.putAt('OthersAdjustment',activity!=null ? checkNegativeSign(activity.extension.otherAdjustmentsAmount.amount) : '')

				def billStatusCode = activity?.extension.billStatusTypeCode
				def billStatus
				switch(billStatusCode){
					case '100':
						billStatus = "Paid in full"
						break
					case '101':
						billStatus = "Not paid"
						break;
					case '102':
						billStatus = "Partially paid"
						break;
				}
				activityInfo.putAt('BillStatus',billStatus)

				activityInfo.putAt('PaymentsReceived',activity!=null ? checkNegativeSign(activity.extension.paymentReceivedAmount.amount) : '')
				activityInfo.putAt('OutStandingAmount',activity!=null ? checkNegativeSign(activity.extension.outstandingAmount.amount) : '')
				activityInfo.putAt('SuspenseAmount',activity!=null ? checkNegativeSign(activity.extension.suspenseAmount.amount) : '')
				activityInfo.putAt('BillNumber',activity?.number)
			}
			catch (Exception e) {
				LOGGER.error("Unable to update data: "+e.getMessage())			}
		}
		activityInfo

	}

	static def selfAdminForExcelOutput(Map<String,Object> activityList, def config, def chartName,def id, def tenantId){
		def outputData = [:]
		def data = [:]
		def activities = [] as List
		def productInfo = [] as List
		outputData.putAt('data', data)
		outputData.putAt('logo', chartName)
		def selfAdminDetails = activityList['selfAdminBillProfiles']
		data.putAt('activities', activities)
		data.putAt('BillNumber',selfAdminDetails.number)
		data.putAt('GroupName',selfAdminDetails.extension.entityName)
		data.putAt('GroupID',id)
		data.putAt('GroupNumber',selfAdminDetails.accountNumber)
		data.putAt("BillingPeriod", selfAdminDetails.extension.billFromDate+"-"+selfAdminDetails.extension.billToDate)
		for(def product : selfAdminDetails.extension.productDetails){
			productInfo = buildSelfAdminOutputDate(product,config,tenantId)
			if (productInfo) {

				for(def productDetails:productInfo){
					activities.add(productDetails)
				}
			}
		}
		def totals=getTotalValues(activities)

		for(def activity: activities){

			activity.totalPremiumActual =  checkNegativeSign(decimalFormattingWithComma(activity?.totalPremiumActual))
			activity.totalPremiumEstimated = checkNegativeSign(decimalFormattingWithComma(activity?.totalPremiumEstimated))
		}
		data.putAt('BillingFee','$0.00')
		data.putAt('NonSufficientFundFee','$0.00')
		def feeAmounnts
		if(null!=selfAdminDetails){
			feeAmounnts = selfAdminDetails?.extension?.feeAmounts
		}

		if(!feeAmounnts.isEmpty()){
			for(def feeAmountData:feeAmounnts){
				def feeTypeCode = feeAmountData.feeTypeCode

				if (feeTypeCode == "201") {
					data.putAt("BillingFee", checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(feeAmountData.feeAmount?.amount))))
				} else if (feeTypeCode == "202") {
					data.putAt("NonSufficientFundFee", checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(feeAmountData.feeAmount?.amount))))
				}
			}
		}
		def totalActual=totals.totalActualPremium
		def outStanding=selfAdminDetails?.extension?.outstandingAmount?.amount
		def suspenseAmount=selfAdminDetails.extension?.suspenseAmount?.amount
		data.putAt('TotalEstimatedPremium', checkNegativeSign(decimalFormattingWithComma(totals?.estimatedTotalPremium)))
		data.putAt('submissionStatusTypeCode',getEnumName(selfAdminDetails.extension?.submissionStatusTypeCode,config,tenantId, 'ref_billing_form_data'))
		data.putAt('TotalActualPremium',  checkNegativeSign(decimalFormattingWithComma(totalActual.toFloat())))
		data.putAt('Suspense',checkNegativeSign(decimalFormattingWithComma(suspenseAmount.toFloat())))
		data.putAt('Outstanding',checkNegativeSign(decimalFormattingWithComma(outStanding.toFloat())))
		data.putAt('Total',checkNegativeSign(decimalFormattingWithComma(validateStringAndParseDouble(selfAdminDetails?.dueAmount.amount))))
		return outputData
	}

	static def selfAdminForVoidedActuals(Map<String,Object> activityList, def config, def chartName,def id, def tenantId){
		// convert to output data for deliver file
		def outputData = [:]
		def data = [:]
		def activities = [] as List
		outputData.putAt('data', data)
		outputData.putAt('logo', chartName)
		def selfAdminDetails = activityList['selfAdminBillProfiles']
		data.putAt('activities', activities)
		data.putAt('BillNumber',selfAdminDetails.number)
		data.putAt('GroupName',selfAdminDetails.extension.entityName)
		data.putAt('GroupID',id)
		data.putAt('GroupNumber',selfAdminDetails.accountNumber)
		data.putAt("BillingPeriod", selfAdminDetails.extension.billFromDate+"-"+selfAdminDetails.extension.billToDate)
		for(def product : selfAdminDetails.extension.productDetails){
			def productInfo = buildSelfAdminOutputDate(product,config,tenantId)
			if (productInfo) {
				for(def productDetails:productInfo){
					activities.add(productDetails)
				}
			}
		}
		def totals= getTotalValues(activities)
		data.putAt('submissionStatusTypeCode',getEnumName(selfAdminDetails.extension?.submissionStatusTypeCode,config,tenantId, 'ref_billing_form_data'))

		data.putAt('Total', totals['totalActualPremium'])
		return outputData
	}

	private static def getTotalValues(def activities){
		def totalMap = [:]
		Double estimatedTotalPremium=0.0
		Double totalActualPremium=0.0
		for(def activity: activities){
			estimatedTotalPremium=estimatedTotalPremium+activity.totalPremiumEstimated
			totalActualPremium=totalActualPremium+activity.totalPremiumActual
		}
		totalMap.put("estimatedTotalPremium",estimatedTotalPremium )
		totalMap.put("totalActualPremium",totalActualPremium )
		totalMap
	}
	private static def buildSelfAdminOutputDate (product, def config, def tenantId){
		def activityInfoList = [] as List
		if(product){
			try{
				def productName= product.receivable[0].receivableCode
				def choice=product.getAt("product").getAt("typeCode")
				def coverageType=getEnumName(productName,config,tenantId, 'ref_self_admin_details')
				def choices=getEnumName(choice,config,tenantId, 'ref_self_admin_details')
				def recievable = product?.getAt('receivable')
				for(def receivableObj: recievable){
					def activityInfo = [:]
					activityInfo.putAt('coverageType',coverageType)
					activityInfo.putAt('choice',choices)
					def rate=validateStringAndParseDouble(receivableObj?.rate)
					def estimatedLives=validateStringAndParseDouble(receivableObj?.estimatedLives)
					def estimatedVolume = receivableObj?.estimatedVolume
					def actualLives=validateStringAndParseDouble(receivableObj?.actualLives)
					def actualVolume=receivableObj?.actualVolume
					def totalPremiumActual= validateStringAndParseDouble(receivableObj?.actualPremium.amount)
					def totalPremiumEstimated = validateStringAndParseDouble(receivableObj?.estimatedPremium.amount)
					def className=receivableObj?.class.getAt("name")
					activityInfo.putAt('className',className)
					def state = receivableObj?.state
					//Set teir for non-tier based coverages based on receivable code else pass tier code as is
					def tierCode = setTierBasedOnReceivableCode(receivableObj?.receivableCode, receivableObj?.tierCode)
					if (tierCode.contains("Spouse") || tierCode.contains("Children")) {
						activityInfo.putAt('coverageTier',tierCode)

					} else {
						if (state != null && !state.isEmpty()) {
							activityInfo.putAt('coverageTier',getEnumName(receivableObj.tierCode,config,tenantId,'ref_self_admin_details')+', '+state)
						} else {
							activityInfo.putAt('coverageTier',getEnumName(receivableObj.tierCode,config,tenantId,'ref_self_admin_details'))
						}
					}


					if((receivableObj.fromAge)!=null && (receivableObj.toAge)!=null){
						activityInfo.putAt('ageBand',receivableObj.fromAge+'-'+receivableObj.toAge)
					}else{
						activityInfo.putAt('ageBand',"")
					}
					activityInfo.putAt('volumeEstimated',estimatedVolume)
					activityInfo.putAt('volumeActual',actualVolume)
					activityInfo.putAt('rate',checkNegativeSign(rate))
					activityInfo.putAt('lifeCountEstimated',estimatedLives)
					activityInfo.putAt('totalPremiumEstimated',  totalPremiumEstimated)
					activityInfo.putAt('lifeCountActual',actualLives)
					activityInfo.putAt('totalPremiumActual', totalPremiumActual)
					activityInfoList.add(activityInfo)
				}
			}catch(Exception e){
				LOGGER.error("Unable to update data: "+e.getMessage());
			}
		}
		activityInfoList
	}

	static def getEnumName(code,config, tenantId, configurationId){
		def billMethodMap=[:]

		code = code.toString()
		if(billMethodMap[code]){
			billMethodMap[code]
		}else {
			def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
			billMethodMap = statusMapping?.data
			billMethodMap[code]
		}
	}

	private static Map<String, Object> getJsonDataAsMap(String filename) {
		JSONParser parser = new JSONParser()
		Map<String, Object> obj =null
		try {
			obj = (JSONObject) parser.parse(new FileReader(filename))
		}
		catch (FileNotFoundException | ParseException e) {
			LOGGER.error("IO Exception: "+e.getMessage())
		}
		return obj
	}

	static String createExcelFile(def excelOutputFileName, byte[] xlsData){
		File excelfile = new File(excelOutputFileName + '.xls')
		OutputStream out = new java.io.FileOutputStream(excelfile)
		out = new java.io.BufferedOutputStream(out)
		FileOutputStream str = new FileOutputStream(excelfile)
		str.write(xlsData)
		str.close()
		out.close()
		def filename =  excelfile.getAbsolutePath()
		return filename
	}

	static String createExcelFileForDeduction(def excelOutputFileName, byte[] xlsData){
		def excelfile = new File(excelOutputFileName + '.xls')
		OutputStream out1 = new java.io.FileOutputStream(excelfile)
		out1 = new java.io.BufferedOutputStream(out1)
		FileOutputStream str1 = new FileOutputStream(excelfile)
		def filename =  excelfile.getAbsolutePath()
		LOGGER.info ("filename----------------------->"+filename)
		str1.write(xlsData)
		str1.close()
		out1.close()
		HSSFWorkbook wb = new HSSFWorkbook(new FileInputStream(filename));
		LOGGER.info ("HSSFWorkbook----------------------->"+wb)
		HSSFSheet sheet = wb.getSheetAt(0);
		HSSFCellStyle my_style = wb.createCellStyle();
		HSSFCellStyle my_style_hidden = wb.createCellStyle();
		HSSFCellStyle my_style_clone = wb.createCellStyle();
		HSSFCellStyle my_style_summary = wb.createCellStyle();
		DataFormat format = wb.createDataFormat();
		CellStyle hiddenStyle = wb.createCellStyle();


		HSSFFont my_font=wb.createFont();
		HSSFFont my_font_summary=wb.createFont();
		my_font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		my_font_summary.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		my_style_summary.setAlignment(my_style_summary.ALIGN_CENTER);
		my_style_summary.setVerticalAlignment(CellStyle.ALIGN_FILL);
		HSSFPalette palette = wb.getCustomPalette();
		short colorIndex = 45;
		HSSFRow row;
		HSSFCell cell;
		HSSFRow summaryRow;
		HSSFCell summaryCell;
		def respCheck
		Iterator rows = sheet.rowIterator();
		int count = 0;
		int emptyCount = 0;
		int payrollCount = 0;
		int summaryColourCount = 0;
		int summaryNextRow =0;
		int summaryCount = 0;
		int adjustmentColourCount = 0;
		int adjustmentNextRow =0;
		int adjustmentCount = 0;
		int detailsColourCount = 0;
		int detailsNextRow =0;
		int detailsCount = 0;
		while (rows.hasNext())
		{
			row=(HSSFRow) rows.next();

			Iterator cells = row.cellIterator();
			while (cells.hasNext())
			{
				cell=(HSSFCell) cells.next();

				respCheck = cell.getStringCellValue();
				System.out.println("Cell value is: "+respCheck+" ");
				System.out.println("respCheck value ------->"+respCheck);
				System.out.println("respCheck value ------->"+respCheck.length());
				String str = cell
				System.out.print("String value ------->"+str);
				if ("Payroll Deduction Detail  ".equalsIgnoreCase(cell.toString())){
					payrollCount ++;
				}else if(payrollCount == 1){
					if(respCheck == " "){
						emptyCount++;
					}
					if(respCheck == ' '){
						emptyCount++;
					}
					if(respCheck == null){
						emptyCount++;
					}
					if(respCheck == ""){
						emptyCount++;
					}
					if(respCheck == ''){
						emptyCount++;
					}
					if (cell.toString() !="Payroll Deduction Detail  " && count < 6 && cell.toString() !=" " && cell.toString() !=null &&  respCheck.toString()!= "" ){
						if(count <3){
							if(cell.toString() == "@hide" || cell.equals("@hide") || cell == "@hide"){
								palette.setColorAtIndex(colorIndex, (byte)221, (byte)235, (byte)247);
								my_style_hidden.setDataFormat(format.getFormat(";;;"))
								my_style_hidden.setFillForegroundColor(colorIndex);
								my_style_hidden.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
								cell.setCellStyle(my_style_hidden);
							}else{
								my_style.setFont(my_font);
								palette.setColorAtIndex(colorIndex, (byte)221, (byte)235, (byte)247);
								my_style.setFillForegroundColor(colorIndex);
								my_style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
								cell.setCellStyle(my_style);
							}
						}else{
							my_style_clone.setFont(my_font);
							cell.setCellStyle(my_style_clone);
						}
						count++;
					}else{
						System.out.print(cell.getStringCellValue()+" ");
					}
				} else{
					if ("Summary Totals ".equalsIgnoreCase(cell.toString())){
						summaryColourCount ++;
						summaryNextRow ++;
					}else if(summaryColourCount == 1 && summaryNextRow < 2 && cell.toString() !=" " && cell.toString() !=null &&  respCheck.toString()!= "") {
						my_style_summary.setFont(my_font_summary);
						palette.setColorAtIndex(colorIndex, (byte)221, (byte)235, (byte)247);
						my_style_summary.setFillForegroundColor(colorIndex);
						my_style_summary.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
						cell.setCellStyle(my_style_summary);
						summaryCount ++;

					}
					else if("Bill Adjustments ".equalsIgnoreCase(cell.toString())){
						adjustmentColourCount ++;
						adjustmentNextRow ++;
					}else if(adjustmentColourCount == 1 && adjustmentNextRow < 2 && cell.toString() !=" " && cell.toString() !=null &&  respCheck.toString()!= "") {
						my_style_summary.setFont(my_font_summary);
						palette.setColorAtIndex(colorIndex, (byte)221, (byte)235, (byte)247);
						my_style_summary.setFillForegroundColor(colorIndex);
						my_style_summary.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
						cell.setCellStyle(my_style_summary);
						adjustmentCount ++;

					}
					else if("Bill Details".equalsIgnoreCase(cell.toString())){
						detailsColourCount ++;
						detailsNextRow ++;
					}else if(detailsColourCount == 1 && detailsNextRow < 2 && cell.toString() !=" " && cell.toString() !=null &&  respCheck.toString()!= "") {
						my_style_summary.setFont(my_font_summary);
						palette.setColorAtIndex(colorIndex, (byte)221, (byte)235, (byte)247);
						my_style_summary.setFillForegroundColor(colorIndex);
						my_style_summary.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
						cell.setCellStyle(my_style_summary);
						detailsCount ++;

					}
				}
			}
			if (emptyCount >= 3){
				count = 0;
			}
			emptyCount =0;
			if(summaryCount >=4){
				summaryColourCount =0;
				summaryNextRow = 0;
			}
			if(adjustmentCount >=12){
				adjustmentColourCount =0;
				adjustmentNextRow = 0;
			}
			if(detailsCount >=12){
				detailsColourCount =0;
				detailsNextRow = 0;
			}
		}
		FileOutputStream out = new FileOutputStream(filename);
		wb.write(out);
		out.close();
		return filename
	}

	static def validateStringAndParseDouble(Object o){
		def result = o
		if(o instanceof String){
			if(!(o == null || "null".equals(o) || "".equals(o))){
				result =Double.parseDouble(o)
			}
		}
		result
	}

	static def checkNegativeSign(def amount){
		amount = amount.toString()
		if(amount.charAt(0) == '-'){
			amount = '-$'+amount.substring(1)}
		else{
			amount = '$'+amount
		}
		amount
	}
	static def decimalFormattingWithComma(def value){

		new DecimalFormat("#,##0.00").format(value)

	}
	/**
	 * Used to retrieve the query Params in Map
	 *
	 * @param queryParams
	 *
	 * @return
	 */
	static def getQueryMapData(queryParams, rsqlParser){
		Node rootNode = rsqlParser.parse(queryParams)
		def gsspRSQLVisitor = new GsspRSQLVisitor()
		def nodes=rootNode.accept(gsspRSQLVisitor)
		LOGGER.trace('nodes'+nodes)
		gsspRSQLVisitor.getMap()
	}
	static def setTierBasedOnReceivableCode(receivableCode, tierCode) {
		if (receivableCode != null && !receivableCode.isEmpty()) {
			if (receivableCode == 'BDEPLS' || receivableCode == 'DEPLS' || receivableCode == 'DADDS') {
				tierCode = 'Spouse Only'
			} else if (receivableCode == 'BDEPLC' || receivableCode == 'DEPLC' || receivableCode == 'DADDC') {
				tierCode = 'Children Only'
			} else if (receivableCode == 'BDEPLS AND BDEPLC') {
				tierCode = 'Spouse + Children'
			} else {
				tierCode
			}
			LOGGER.debug("DownloadUtil:setTierBasedOnReceivableCode tierCode is: "+tierCode)
		} else {
			tierCode
			LOGGER.debug("DownloadUtil:setTierBasedOnReceivableCode receivableCode is empty")
		}
		tierCode
	}

	//Anti Virus Scan Start
	static def antiVirusSecurityScanCode(workFlow,bytesBase64){
		def securityScan = workFlow.getEnvPropertyFromContext("securityScan")
		if(securityScan?.equalsIgnoreCase("true")){
			RetrieveScanStatus retrieveScanStatus = new RetrieveScanStatus()
			workFlow.addFacts("content", bytesBase64)
			retrieveScanStatus.execute(workFlow)
			def finalResponse=workFlow.getFact("response",String.class)
			def clean = finalResponse?.get("clean")
			LOGGER.info "Anti Virus - clean : " + clean
			def message = finalResponse?.get("message")
			LOGGER.info "Anti Virus - message : " + message
			if(clean == false){
				throw new GSSPException("INVALID_FILE")
			}
		}
	}
	//Anti Virus Scan End
}
