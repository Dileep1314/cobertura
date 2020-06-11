<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.1"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format"
	exclude-result-prefixes="fo">
	<xsl:template match="root">
		<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
			<fo:layout-master-set>
				<fo:simple-page-master master-name="my-page">
					<fo:region-body margin="0.5in" />
				</fo:simple-page-master>
			</fo:layout-master-set>
			<fo:page-sequence master-reference="my-page">
				<fo:flow flow-name="xsl-region-body">
				<fo:block font-weight="bold" text-align="center" padding-bottom="25px" font-size="15px">
						<xsl:value-of select="smd_econsent_heading_one" />
					</fo:block>
					<fo:block text-align="justify" padding-bottom="15px" font-size="10px">
						<xsl:value-of select="smd_econsent_heading_content" />
						</fo:block>
					<fo:block text-align="left" padding-bottom="15px" font-size="10px">
						<xsl:value-of select="smd_econsent_terms" />
					</fo:block>
					
						<fo:list-block>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_terms" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_right_to_consent" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_right_to_consent_content" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_withdrawal_to_consent" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_withdrawal_to_consent_content" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_withdrawal_to_consent_a" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_withdrawal_to_consent_b" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_withdrawal_to_consent_c" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							
						</fo:list-block>
						<fo:block text-align="Justify" padding-bottom="15px" font-size="10px">
							<xsl:value-of select="smd_econsent_delivery_of_document" />
						</fo:block>
						<fo:block text-align="Justify" padding-bottom="15px" font-size="10px">
							<xsl:value-of select="smd_econsent_delivery_of_document_content" />
						</fo:block>
						<fo:block text-align="Justify" padding-bottom="15px" font-size="10px">
							<xsl:value-of select="smd_econsent_obtain_paper_copy_fees" />
						</fo:block>
						<fo:list-block>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_delivery_of_electronic_document" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_delivery_of_electronic_document_a" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							<fo:list-item space-after="0.5ex">
								<fo:list-item-label start-indent="1em">
									<fo:block font-size="10px">&#x2022;</fo:block>
								</fo:list-item-label>
								<fo:list-item-body>
									<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
										<xsl:value-of select="smd_econsent_delivery_of_electronic_document_b" />
									</fo:block>
								</fo:list-item-body>
							</fo:list-item>
							
						</fo:list-block>
						<fo:block text-align="Justify" padding-bottom="15px" font-size="10px">
							<xsl:value-of select="smd_econsent_materials_promptly" />
						</fo:block>
						<fo:block text-align="Justify" padding-bottom="15px" font-size="10px">
							<xsl:value-of select="smd_econsent_materials_promptly_content" />
						</fo:block>
						
					</fo:flow>
			</fo:page-sequence>
		</fo:root>
	</xsl:template>
</xsl:stylesheet>