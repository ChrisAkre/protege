package dev.akre.it.hibernate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageAnnotationTest {

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
    public void testCustomerQueryByName() {
        // Setup data
        EntityProtos.Customer customer = EntityProtos.Customer.newBuilder()
                .setName("Charlie")
                .build();

        em.getTransaction().begin();
        em.persist(customer);
        em.getTransaction().commit();
        em.clear();

        // Simulate query
        EntityProtos.Customer result = em.createQuery("SELECT c FROM Customer c WHERE c.name = :name", EntityProtos.Customer.class)
                .setParameter("name", "Charlie")
                .getSingleResult();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Charlie");
        assertThat(result.getId()).isGreaterThan(0);
    }

    @Test
    public void testCustomerUpsert() {
        // Initial insert
        EntityProtos.Customer customer = EntityProtos.Customer.newBuilder()
                .setName("David")
                .build();

        em.getTransaction().begin();
        em.persist(customer);
        em.getTransaction().commit();
        long id = customer.getId();
        em.clear();

        // Simulate upsert
        EntityProtos.Customer updateReq = EntityProtos.Customer.newBuilder()
                .setId(id)
                .setName("David Updated")
                .build();

        em.getTransaction().begin();
        EntityProtos.Customer updated = em.merge(updateReq);
        em.getTransaction().commit();

        assertThat(updated.getName()).isEqualTo("David Updated");

        em.clear();
        EntityProtos.Customer found = em.find(EntityProtos.Customer.class, id);
        assertThat(found.getName()).isEqualTo("David Updated");
    }
}
