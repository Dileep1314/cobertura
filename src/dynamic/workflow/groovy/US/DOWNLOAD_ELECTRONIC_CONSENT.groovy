package groovy.US

import groovy.UTILS.BillingConstants
import groovy.json.JsonBuilder
import com.metlife.gssp.taskflow.Task
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory

import com.metlife.gssp.common.pdf.PdfGeneration.PdfImplementationTypes
import java.lang.reflect.Array
import net.minidev.json.parser.JSONParser
import com.metlife.domain.model.EntityResult
import org.springframework.http.HttpStatus
import com.metlife.gssp.common.pdf.PdfGeneration
import com.metlife.gssp.common.pdf.impl.PdfGenerationFactory
import com.metlife.gssp.configuration.GSSPConfiguration

class DOWNLOAD_ELECTRONIC_CONSENT implements Task{

	private static final Logger LOGGER = LoggerFactory.getLogger(DOWNLOAD_ELECTRONIC_CONSENT)
	private static final STATE="california"
	@Override
	public Object execute(WorkflowDomain workFlow) {
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def requestBodyMap = workFlow.getRequestBody()
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def stateId = requestBodyMap['state']
		def fileData
		def responseArray = [] as Set
		def responseMap = ['files' : responseArray]
		PdfGenerationFactory factory = workFlow.getBeanFromContext('pdfGenerationFactory', PdfGenerationFactory.class)
		PdfGeneration pdfImpl = factory.getPdfGenerationImplementation(PdfImplementationTypes.XSLFOP)
		def getpdfKeys= getConfigValue('PDF_KEY',config,tenantId, 'ref_billing_form_data','en_US')
		def pdfKeys=[] as Array
		pdfKeys = getpdfKeys.split(";")
		def keyMap = buildKeyMap(pdfKeys, config, tenantId,'ref_billing_econsent_data',stateId)
		def outputJson = new JsonBuilder(["root":keyMap]).toPrettyString()
		JSONParser parser = new JSONParser()
		Map<String, Object> jsonMap = parser.parse(outputJson)
		byte [] pdfByte
		if(STATE.equals(stateId)){
			pdfByte=pdfImpl.generatePdf(jsonMap, "econsent-california-pdf.xsl")
		}
		else{
			pdfByte=pdfImpl.generatePdf(jsonMap, "econsent-pdf.xsl")
		}
		fileData = ['content' : pdfByte.encodeBase64().toString()]
		fileData.contentLength = pdfByte.encodeBase64().toString().length()
		fileData.encodingType = BillingConstants.BASE64
		fileData.formatCode = BillingConstants.PDF
		fileData.name = BillingConstants.E_CONSENT_FILE_NAME
		responseArray.add(fileData)
		workFlow.addResponseBody(new EntityResult([Details:responseMap], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	static String createPdfFile(def pdfOutputFileName, byte[] pdfData){
		def filename
		try {
			File pdffile = new File(pdfOutputFileName + BillingConstants.DOT + BillingConstants.PDF)
			OutputStream out = new java.io.FileOutputStream(pdffile)
			out = new java.io.BufferedOutputStream(out)
			FileOutputStream str = new FileOutputStream(pdffile)
			str.write(pdfData)
			str.close()
			out.close()
			filename =  pdffile.getAbsolutePath()
		}catch(Exception e) {
			LOGGER.error("Error occoured during creation of pdf:"+e.getMessage())
		}
		return filename
	}
	static def getConfigValue(keycode, config, tenantId, configurationId, locale){
		def billMethodMap=[:]

		keycode = keycode.toString()
		if(billMethodMap[keycode]){
			billMethodMap[keycode]
		}else {
			def statusMapping = config.get(configurationId, tenantId, [locale : locale])
			billMethodMap = statusMapping?.data
			billMethodMap[keycode]
		}
	}
	static def buildKeyMap(pdfKeys, config, tenantId, configurationId, stateId){
		if(!STATE.equals(stateId)){
			stateId="other_state"
		}
		def keyMaping = [:]
		for(def pdfKey:pdfKeys)	{
			if(pdfKey == null){
				break;
			}
			else{
				keyMaping.putAt(pdfKey,getConfigValue(pdfKey,config,tenantId,configurationId,stateId))
		}}
		keyMaping
	}
}
