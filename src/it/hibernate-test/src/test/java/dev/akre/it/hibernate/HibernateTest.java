package dev.akre.it.hibernate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    // --- Scenario 1: Field annotations (User) ---

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

    // --- Scenario 2: Interface based (Product) ---

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


    // --- Scenario 3: Message annotation option (Customer) ---

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
