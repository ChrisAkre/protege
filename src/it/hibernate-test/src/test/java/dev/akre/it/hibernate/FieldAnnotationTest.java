package dev.akre.it.hibernate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldAnnotationTest {

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
    public void testUserQueryByName() {
        // Setup data
        EntityProtos.User user = EntityProtos.User.newBuilder().setName("Alice").build();
        em.getTransaction().begin();
        em.persist(user);
        em.getTransaction().commit();
        em.clear();

        // Simulate gRPC service queryByName
        EntityProtos.User result = em.createQuery("SELECT u FROM User u WHERE u.name = :name", EntityProtos.User.class)
                .setParameter("name", "Alice")
                .getSingleResult();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getId()).isGreaterThan(0);
    }

    @Test
    public void testUserUpsert() {
        // Initial insert
        EntityProtos.User user = EntityProtos.User.newBuilder().setName("Bob").build();
        em.getTransaction().begin();
        em.persist(user);
        em.getTransaction().commit();
        long id = user.getId();
        em.clear();

        // Simulate gRPC service upsert (update existing)
        EntityProtos.User updateReq = EntityProtos.User.newBuilder()
                .setId(id)
                .setName("Bob Updated")
                .build();

        em.getTransaction().begin();
        // Upsert logic using merge
        EntityProtos.User updated = em.merge(updateReq);
        em.getTransaction().commit();

        assertThat(updated.getName()).isEqualTo("Bob Updated");
        assertThat(updated.getId()).isEqualTo(id);

        // Verify in DB
        em.clear();
        EntityProtos.User found = em.find(EntityProtos.User.class, id);
        assertThat(found.getName()).isEqualTo("Bob Updated");
    }
}
