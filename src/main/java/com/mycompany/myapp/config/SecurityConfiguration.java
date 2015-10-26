package com.mycompany.myapp.config;

import com.mycompany.myapp.security.*;
import com.mycompany.myapp.web.filter.CsrfCookieGeneratorFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import org.springframework.data.repository.query.spi.EvaluationContextExtension;
import org.springframework.data.repository.query.spi.EvaluationContextExtensionSupport;
import org.springframework.http.MediaType;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.data.repository.query.SecurityEvaluationContextExtension;

import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;

import javax.inject.Inject;
import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Inject
    private UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Override
    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
            .userDetailsService(userDetailsService)
            .passwordEncoder(passwordEncoder());
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring()
            .antMatchers("/scripts/**/*.{js,html}")
            .antMatchers("/bower_components/**")
            .antMatchers("/i18n/**")
            .antMatchers("/assets/**")
            .antMatchers("/swagger-ui/index.html")
            .antMatchers("/api/register")
            .antMatchers("/api/activate")
            .antMatchers("/api/account/reset_password/init")
            .antMatchers("/api/account/reset_password/finish")
            .antMatchers("/test/**");
    }


    @Configuration
    @EnableAuthorizationServer
    public static class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {

        private static final String OAUTH_SECURITY = "jhipster.security.authentication.oauth.";
        private static final String CLIENTID = "clientid";
        private static final String SECRET = "secret";
        private static final String TOKEN_VALIDATION_TIME = "tokenValidityInSeconds";

        @Autowired
        private AuthenticationManager authenticationManager;

        @Override
        public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {
            oauthServer.tokenKeyAccess("isAnonymous() || hasAuthority('"+AuthoritiesConstants.USER+"')").checkTokenAccess("hasAuthority('"+AuthoritiesConstants.USER+"')");
        }

        @Override
        public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
            endpoints
                .authenticationManager(authenticationManager)
                .tokenStore(tokenStore())
            ;
        }

        @Inject
        private Environment env;
        @Inject
        private DataSource dataSource;

        @Bean
        public TokenStore tokenStore() {
            return new JdbcTokenStore(dataSource);
        }


        @Override
        public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
            clients
                .inMemory()
                .withClient(env.getProperty(OAUTH_SECURITY + CLIENTID))
                .scopes("read", "write")
                .authorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.USER)
                .authorizedGrantTypes("password", "refresh_token")
                .secret(env.getProperty(OAUTH_SECURITY + SECRET))
                .accessTokenValiditySeconds(env.getProperty(OAUTH_SECURITY + TOKEN_VALIDATION_TIME, Integer.class, 18000));

        }
    }


    @Configuration
    @Order(1)
    public static class SecurityWebConfiguration extends WebSecurityConfigurerAdapter {
        @Inject
        private Environment env;

        @Inject
        private AjaxAuthenticationSuccessHandler ajaxAuthenticationSuccessHandler;

        @Inject
        private AjaxAuthenticationFailureHandler ajaxAuthenticationFailureHandler;

        @Inject
        private AjaxLogoutSuccessHandler ajaxLogoutSuccessHandler;

        @Inject
        private RememberMeServices rememberMeServices;


        @Override
        protected void configure(HttpSecurity http) throws Exception {


            http
                .csrf().disable().authorizeRequests()
                .and()
                .formLogin()
                .loginProcessingUrl("/api/authentication")
                .successHandler(ajaxAuthenticationSuccessHandler)
                .failureHandler(ajaxAuthenticationFailureHandler)
                .usernameParameter("j_username")
                .passwordParameter("j_password")
                .permitAll()
                .and()
                .rememberMe()
                .rememberMeServices(rememberMeServices)
                .key(env.getProperty("jhipster.security.rememberme.key"))
                .and()
                .logout()
                .logoutUrl("/api/logout")
                .logoutSuccessHandler(ajaxLogoutSuccessHandler)
                .deleteCookies("JSESSIONID")
                .permitAll()
                .and()
                .exceptionHandling()
            ;



        }

    }


    @Order(2)
    @Configuration
    @EnableResourceServer
    public static class ResourceServerConfiguration extends ResourceServerConfigurerAdapter {
        @Inject
        private Http401UnauthorizedEntryPoint authenticationEntryPoint;

        @Inject
        private AjaxLogoutSuccessHandler ajaxLogoutSuccessHandler;


        @Override
        public void configure(HttpSecurity http) throws Exception {
            ContentNegotiationStrategy contentNegotiationStrategy = http.getSharedObject(ContentNegotiationStrategy.class);
            if (contentNegotiationStrategy == null) {
                contentNegotiationStrategy = new HeaderContentNegotiationStrategy();
            }
            MediaTypeRequestMatcher preferredMatcher = new MediaTypeRequestMatcher(contentNegotiationStrategy,
                MediaType.APPLICATION_FORM_URLENCODED,
                MediaType.APPLICATION_JSON,
                MediaType.MULTIPART_FORM_DATA);


            http
                .csrf().disable().authorizeRequests()
                .and()
                .anonymous()
                .disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .httpBasic()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint)
                .defaultAuthenticationEntryPointFor(authenticationEntryPoint, preferredMatcher)
                .and()
                .authorizeRequests()
                .antMatchers("/api/**").fullyAuthenticated();


        }
    }



}
