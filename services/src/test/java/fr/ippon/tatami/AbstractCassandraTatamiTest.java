package fr.ippon.tatami;

import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.repository.CounterRepository;
import fr.ippon.tatami.service.util.DomainUtil;
import fr.ippon.tatami.test.application.ApplicationTestConfiguration;
import fr.ippon.tatami.test.application.WebApplicationTestConfiguration;
import org.cassandraunit.DataLoader;
import org.cassandraunit.dataset.json.ClassPathJsonDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import javax.inject.Inject;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(
                name = "root",
                classes = ApplicationTestConfiguration.class),
        @ContextConfiguration(
                name = "dispatcher",
                classes = WebApplicationTestConfiguration.class
        )
})
public abstract class AbstractCassandraTatamiTest {

    protected final Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    private static boolean isInitialized = false;

    private static final Object lock = new Object();

    protected static Client client = null;

    protected static String defaultUser = "jdubois@ippon.fr";

    protected static String defaultDomain = "ippon.fr";

    @Inject
    private CounterRepository counterRepository;

    @Inject
    private UserDetailsService userDetailsService;

    @BeforeClass
    public static void beforeClass() throws Exception {
        synchronized (lock) {
            if (!isInitialized) {
                EmbeddedCassandraServerHelper.startEmbeddedCassandra();
                // create structure and load data
                String clusterName = "Tatami cluster";
                String host = "localhost:9171";
                DataLoader dataLoader = new DataLoader(clusterName, host);
                dataLoader.load(new ClassPathJsonDataSet("dataset/dataset.json"));

                final ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder();
                builder.put("cluster.name", clusterName);

                final Node node = NodeBuilder.nodeBuilder().settings(builder.build()).local(true).node();
                client = node.client();

                isInitialized = true;
            }
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    protected User constructAUser(String login, String firstName, String lastName) {
        User user = new User();
        user.setLogin(login.toLowerCase());
        user.setPassword("");
        user.setUsername(DomainUtil.getUsernameFromLogin(user.getLogin()));
        user.setDomain(DomainUtil.getDomainFromLogin(user.getLogin()));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setJobTitle("web developer");
        counterRepository.createStatusCounter(user.getLogin());
        counterRepository.createFriendsCounter(user.getLogin());
        counterRepository.createFollowersCounter(user.getLogin());

        // Create the security context
        UserDetails userDetails = userDetailsService.loadUserByUsername(login);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(), userDetails.getAuthorities());
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        return user;
    }

    protected User constructAUser(String login) {
        return constructAUser(login, null, null);
    }

}