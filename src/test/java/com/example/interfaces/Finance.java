package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

import java.util.Map;

@GenProto(value = "finance.proto", pkg = "com.example.proto", outerClass = true)
public class Finance {

    public interface Transaction {
        @Field(1)
        String id();
        @Field(2)
        double amount();
        @Field(3)
        String currency();
        @Field(4)
        Type type();
        @Field(5)
        Party sender();
        @Field(6)
        Party receiver();
        @Field(value = 7, type = Field.ProtoFieldType.TYPE_FIXED64)
        long internal_trace_id();

        enum Type {
            DEBIT, CREDIT, TRANSFER
        }

        interface Party {
            @Field(1)
            String account_number();
            @Field(2)
            String bank_code();
        }
    }

    public interface AccountBalance {
        @Field(1)
        String account_id();
        @Field(2)
        Map<String, Double> balances_by_currency();
    }
}
