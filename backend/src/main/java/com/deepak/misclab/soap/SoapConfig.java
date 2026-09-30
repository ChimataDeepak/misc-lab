package com.deepak.misclab.soap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/** WSDL generated from the XSD, served at /ws/receipts.wsdl (Spring Boot registers MessageDispatcherServlet on /ws). */
@Configuration
public class SoapConfig {
    public static final String NS = "http://deepak.com/misclab/receipts";

    @Bean
    XsdSchema receiptsSchema() { return new SimpleXsdSchema(new ClassPathResource("xsd/receipts.xsd")); }

    @Bean(name = "receipts")
    DefaultWsdl11Definition receiptsWsdl(XsdSchema receiptsSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("ReceiptsPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NS);
        wsdl.setSchema(receiptsSchema);
        return wsdl;
    }
}
