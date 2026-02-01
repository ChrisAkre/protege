package dev.akre.it.hibernate;

import dev.akre.protege.Field;
import dev.akre.protege.GenProto;
import jakarta.persistence.*;

@GenProto(value = "Product")
@Entity
@Table(name = "products")
public interface ProductDef {

    @Field(1)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long getId();

    @Field(2)
    @Column(nullable = false)
    String getName();

    @Field(3)
    @Column(nullable = false)
    double getPrice();
}
