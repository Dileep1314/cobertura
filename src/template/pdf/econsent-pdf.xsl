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
							<fo:list-item-label >
								<fo:block font-size="10px" font-weight="bold">
									<fo:inline font-size="10px" font-weight="bold">1</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_right_to_consent" /></fo:inline>
									<xsl:value-of select="smd_econsent_right_to_consent_content" />
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block >
									<fo:inline font-size="10px" font-weight="bold">2</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_withdrawal_to_consent" /></fo:inline>
									<xsl:value-of select="smd_econsent_withdrawal_to_consent_content" />
								</fo:block>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
										<fo:list-block>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">(a)</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2.5em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_withdrawal_to_consent_a" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">(b)</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2.5em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_withdrawal_to_consent_b" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">(c)</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2.5em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_withdrawal_to_consent_c" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
										</fo:list-block>
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block font-size="10px" font-weight="bold">
									<fo:inline font-size="10px" font-weight="bold">3</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_delivery_of_document" /></fo:inline>
									<xsl:value-of select="smd_econsent_delivery_of_document_content" />
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block font-size="10px" font-weight="bold">
									<fo:inline font-size="10px" font-weight="bold">4</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_obtain_paper_copy_fees" /></fo:inline>
									<xsl:value-of select="smd_econsent_obtain_paper_copy_fees_content" />
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block >
									<fo:inline font-size="10px" font-weight="bold">5</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_delivery_of_electronic_document" /></fo:inline>
									
								</fo:block>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
										<fo:list-block>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">(a)</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2.5em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_delivery_of_electronic_document_a" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">(b)</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2.5em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_delivery_of_electronic_document_b" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
										</fo:list-block>
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block font-size="10px" font-weight="bold">
									<fo:inline font-size="10px" font-weight="bold">6</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_materials_promptly" /></fo:inline>
									<xsl:value-of select="smd_econsent_materials_promptly_content" />
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block font-size="10px" font-weight="bold">
									<fo:inline font-size="10px" font-weight="bold">7</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_updating_information" /></fo:inline>
									<xsl:value-of select="smd_econsent_updating_information_content" />
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block font-size="10px" font-weight="bold">
									<fo:inline font-size="10px" font-weight="bold">8</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_hardware_software_requirements" /></fo:inline>
									<xsl:value-of select="smd_econsent_hardware_software_requirements_content" />
								</fo:block>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<xsl:value-of select="smd_econsent_hardware_software_requirements_content_message1" />
								</fo:block>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<xsl:value-of select="smd_econsent_hardware_software_requirements_content_message2" />
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
						<fo:list-item space-after="0.5ex">
							<fo:list-item-label >
								<fo:block >
									<fo:inline font-size="10px" font-weight="bold">9</fo:inline>.
								</fo:block>
							</fo:list-item-label>
							<fo:list-item-body>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
									<fo:inline font-weight="bold"><xsl:value-of select="smd_econsent_electronically" /></fo:inline>
								</fo:block>
								<fo:block start-indent="1em" text-align="justify" padding-bottom="5px" font-size="10px">
										<fo:list-block>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message1" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message2" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message3" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message4" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message5" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message6" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message7" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message8" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message9" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_electronically_message10" />
													</fo:block>
													<fo:block text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_disclaimer" />
													</fo:block>
												</fo:list-item-body>
											</fo:list-item>
											<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_disclaimer_message1" />
													</fo:block>													
												</fo:list-item-body>
											</fo:list-item>
										<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_disclaimer_message2" />
													</fo:block>													
												</fo:list-item-body>
											</fo:list-item>
										<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_disclaimer_message3" />
													</fo:block>													
												</fo:list-item-body>
											</fo:list-item>
										<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_disclaimer_message4" />
													</fo:block>													
												</fo:list-item-body>
											</fo:list-item>
										<fo:list-item space-after="0.5ex">
												<fo:list-item-label >
													<fo:block font-size="10px">&#x2022;</fo:block>
												</fo:list-item-label>
												<fo:list-item-body>
													<fo:block start-indent="2em" text-align="justify" padding-bottom="5px" font-size="10px">
														<xsl:value-of select="smd_econsent_disclaimer_message5" />
													</fo:block>													
												</fo:list-item-body>
											</fo:list-item>
										</fo:list-block>
								</fo:block>
							</fo:list-item-body>
						</fo:list-item>
					</fo:list-block>
					
					</fo:flow>
			</fo:page-sequence>
		</fo:root>
	</xsl:template>
</xsl:stylesheet>