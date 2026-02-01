package dev.akre.it.hibernate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterfaceGenerationTest {

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeEach
    public void setUp() {
        emf = Persistence.createEntityManagerFactory("test-pu");
        em = emf.createEntityManager();
    }

    @AfterEach
    public void tearDown() {
        if (em != null) {
            em.close();
        }
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    public void testProductQueryByName() {
        // Setup data
        // Assuming Product generated class has builder and same structure
        ProductOuterClass.Product product = ProductOuterClass.Product.newBuilder()
                .setName("Gadget")
                .setPrice(19.99)
                .build();

        em.getTransaction().begin();
        em.persist(product);
        em.getTransaction().commit();
        em.clear();

        // Simulate gRPC service queryByName
        ProductOuterClass.Product result = em.createQuery("SELECT p FROM Product p WHERE p.name = :name", ProductOuterClass.Product.class)
                .setParameter("name", "Gadget")
                .getSingleResult();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Gadget");
        assertThat(result.getPrice()).isEqualTo(19.99);
        assertThat(result.getId()).isGreaterThan(0);
    }

    @Test
    public void testProductUpsert() {
        // Initial insert
        ProductOuterClass.Product product = ProductOuterClass.Product.newBuilder()
                .setName("Widget")
                .setPrice(5.00)
                .build();

        em.getTransaction().begin();
        em.persist(product);
        em.getTransaction().commit();
        long id = product.getId();
        em.clear();

        // Simulate upsert
        ProductOuterClass.Product updateReq = ProductOuterClass.Product.newBuilder()
                .setId(id)
                .setName("Widget V2")
                .setPrice(6.00)
                .build();

        em.getTransaction().begin();
        ProductOuterClass.Product updated = em.merge(updateReq);
        em.getTransaction().commit();

        assertThat(updated.getName()).isEqualTo("Widget V2");
        assertThat(updated.getPrice()).isEqualTo(6.00);

        em.clear();
        ProductOuterClass.Product found = em.find(ProductOuterClass.Product.class, id);
        assertThat(found.getName()).isEqualTo("Widget V2");
    }
}
