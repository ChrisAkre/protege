package dev.akre.it.hibernate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Skipped because Protege features required for Hibernate (JPA annotations) are reverted")
public class HibernateTest {

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
    public void testPersistAndFind() {
        // Create a new User via builder
        EntityProtos.User user = EntityProtos.User.newBuilder()
                .setName("Alice")
                .build();

        // Persist
        em.getTransaction().begin();
        em.persist(user);
        em.getTransaction().commit();

        // Verify ID was generated and assigned
        long generatedId = user.getId();
        assertThat(generatedId).isGreaterThan(0);

        // Clear context to ensure we read from DB
        em.clear();

        // Find by ID
        EntityProtos.User foundUser = em.find(EntityProtos.User.class, generatedId);

        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getName()).isEqualTo("Alice");
        assertThat(foundUser.getId()).isEqualTo(generatedId);
    }

    @Test
    public void testUpdate() {
        // Create a new User via builder
        EntityProtos.User user = EntityProtos.User.newBuilder()
                .setName("Bob")
                .build();

        // Persist
        em.getTransaction().begin();
        em.persist(user);
        em.getTransaction().commit();
        long id = user.getId();
        em.clear();

        // Update: Create a detached instance with same ID and new name
        // Since we can't mutate the managed entity's fields directly (no public setters),
        // we use a detached object and merge it.
        EntityProtos.User detachedUpdate = EntityProtos.User.newBuilder()
                .setId(id)
                .setName("Bob Updated")
                .build();

        em.getTransaction().begin();
        EntityProtos.User mergedUser = em.merge(detachedUpdate);
        em.getTransaction().commit();

        em.clear();

        // Verify update
        EntityProtos.User foundUser = em.find(EntityProtos.User.class, id);
        assertThat(foundUser.getName()).isEqualTo("Bob Updated");
    }
}
