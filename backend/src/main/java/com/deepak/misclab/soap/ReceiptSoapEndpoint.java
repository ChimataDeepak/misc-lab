package com.deepak.misclab.soap;

import com.deepak.misclab.Receipt;
import com.deepak.misclab.ReceiptService;
import com.deepak.misclab.soap.gen.GetReceiptRequest;
import com.deepak.misclab.soap.gen.GetReceiptResponse;
import java.util.GregorianCalendar;
import java.time.ZoneOffset;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * SOAP facade over the same ReceiptService used by REST, GraphQL and gRPC: one domain, four transports.
 * Legacy bank/ERP integrations often speak only SOAP, so the adapter stays thin: map XML <-> domain, nothing else.
 */
@Endpoint
public class ReceiptSoapEndpoint {
    private final ReceiptService receipts;
    private final DatatypeFactory xmlTime;

    public ReceiptSoapEndpoint(ReceiptService receipts) throws DatatypeConfigurationException {
        this.receipts = receipts;
        this.xmlTime = DatatypeFactory.newInstance();
    }

    @PayloadRoot(namespace = SoapConfig.NS, localPart = "GetReceiptRequest")
    @ResponsePayload
    public GetReceiptResponse getReceipt(@RequestPayload GetReceiptRequest request) {
        Receipt r = receipts.get(request.getReceiptId());
        if (r == null) throw new ReceiptNotFoundFault(request.getReceiptId());
        com.deepak.misclab.soap.gen.Receipt x = new com.deepak.misclab.soap.gen.Receipt();
        x.setReceiptId(r.receiptId());
        x.setPayer(r.payer());
        x.setAmount(r.amount());
        x.setCurrency(r.currency());
        x.setUsdAmount(r.usdAmount());
        x.setCreatedAt(xmlTime.newXMLGregorianCalendar(GregorianCalendar.from(r.createdAt().atZone(ZoneOffset.UTC))));
        GetReceiptResponse resp = new GetReceiptResponse();
        resp.setReceipt(x);
        return resp;
    }

    /** Becomes a SOAP Client fault (the caller sent an unknown id), not a server error. */
    @SoapFault(faultCode = FaultCode.CLIENT)
    public static class ReceiptNotFoundFault extends RuntimeException {
        public ReceiptNotFoundFault(String id) { super("receipt not found: " + id); }
    }
}
