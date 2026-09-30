package com.deepak.misclab.soap;

import static org.mockito.Mockito.when;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.*;

import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import javax.xml.transform.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.webservices.server.WebServiceServerTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

/** Contract-first SOAP endpoint through the real MessageDispatcher (no HTTP port), via MockWebServiceClient. */
@WebServiceServerTest(ReceiptSoapEndpoint.class)
class ReceiptSoapEndpointTest {
    static final Map<String, String> NS = Map.of("r", SoapConfig.NS);

    @Autowired MockWebServiceClient client;
    @MockitoBean ReceiptService receipts;

    static Source request(String id) {
        return new StringSource("<r:GetReceiptRequest xmlns:r=\"" + SoapConfig.NS + "\"><r:receiptId>" + id + "</r:receiptId></r:GetReceiptRequest>");
    }

    @Test void getReceiptReturnsSchemaValidResponse() throws Exception {
        when(receipts.get("rc-42")).thenReturn(new Receipt("rc-42", "deepak", new BigDecimal("1000.00"), "INR",
                new BigDecimal("12.00"), Instant.parse("2026-09-30T10:15:30Z")));

        client.sendRequest(withPayload(request("rc-42")))
                .andExpect(noFault())
                .andExpect(validPayload(new ClassPathResource("xsd/receipts.xsd")))
                .andExpect(xpath("/r:GetReceiptResponse/r:receipt/r:receiptId", NS).evaluatesTo("rc-42"))
                .andExpect(xpath("/r:GetReceiptResponse/r:receipt/r:payer", NS).evaluatesTo("deepak"))
                .andExpect(xpath("/r:GetReceiptResponse/r:receipt/r:usdAmount", NS).evaluatesTo("12.00"))
                .andExpect(xpath("/r:GetReceiptResponse/r:receipt/r:createdAt", NS).evaluatesTo("2026-09-30T10:15:30.000Z"));
    }

    @Test void unknownReceiptIsAClientFault() {
        when(receipts.get("nope")).thenReturn(null);
        client.sendRequest(withPayload(request("nope")))
                .andExpect(clientOrSenderFault("receipt not found: nope"));
    }
}
