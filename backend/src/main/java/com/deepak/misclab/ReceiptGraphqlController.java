package com.deepak.misclab;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ReceiptGraphqlController {
    private final ReceiptService service;

    public ReceiptGraphqlController(ReceiptService service) {
        this.service = service;
    }

    @QueryMapping
    public ReceiptView receipt(@Argument String id) {
        Receipt r = service.get(id);
        return r == null ? null : ReceiptView.from(r);
    }
}
