/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2015] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.api.dao.billing;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenRequest;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Result;
import com.braintreegateway.exceptions.BraintreeException;
import com.braintreegateway.exceptions.NotFoundException;
import com.codenvy.api.account.AccountLocker;
import com.codenvy.api.account.billing.BillingPeriod;
import com.codenvy.api.account.billing.BillingService;
import com.codenvy.api.account.billing.CreditCardDao;
import com.codenvy.api.account.billing.CreditCardRegistrationEvent;
import com.codenvy.api.account.billing.ResourcesFilter;
import com.codenvy.api.account.impl.shared.dto.AccountResources;
import com.codenvy.api.account.impl.shared.dto.CreditCard;
import com.codenvy.api.account.subscription.ServiceId;
import com.codenvy.api.account.subscription.service.util.SubscriptionMailSender;

import org.eclipse.che.api.account.server.SubscriptionServiceRegistry;
import org.eclipse.che.api.account.server.dao.AccountDao;
import org.eclipse.che.api.account.server.dao.Subscription;
import org.eclipse.che.api.account.shared.dto.SubscriptionState;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of credit card DAO based on Braintree service.
 *
 * @author Max Shaposhnik (mshaposhnik@codenvy.com) on 1/26/15.
 */
public class BraintreeCreditCardDaoImpl implements CreditCardDao {

    private static final Logger LOG = LoggerFactory.getLogger(BraintreeCreditCardDaoImpl.class);

    private final BraintreeGateway gateway;

    private final EventService eventService;

    private final BillingService billingService;

    private final BillingPeriod billingPeriod;

    private final AccountLocker accountLocker;

    private final AccountDao accountDao;

    private final SubscriptionMailSender subscriptionMailSender;

    private final SubscriptionServiceRegistry registry;

    @Inject
    public BraintreeCreditCardDaoImpl(BraintreeGateway gateway,
                                      EventService eventService,
                                      BillingService billingService,
                                      BillingPeriod billingPeriod,
                                      AccountLocker accountLocker,
                                      AccountDao accountDao,
                                      SubscriptionMailSender subscriptionMailSender,
                                      SubscriptionServiceRegistry registry) {
        this.gateway = gateway;
        this.eventService = eventService;
        this.billingService = billingService;
        this.billingPeriod = billingPeriod;
        this.accountLocker = accountLocker;
        this.accountDao = accountDao;
        this.subscriptionMailSender = subscriptionMailSender;
        this.registry = registry;
    }

    @Override
    public String getClientToken(String accountId) throws ServerException, ForbiddenException {
        if (accountId == null) {
            throw new ForbiddenException("Account ID required.");
        }
        try {
            String token = gateway.clientToken().generate(new ClientTokenRequest().customerId(accountId));
            if (token == null) { // e.g. account not yet registered in btree
                return gateway.clientToken().generate();
            }
            return token;
        } catch (BraintreeException e) {
            LOG.warn("Braintree exception: ", e);
            throw new ServerException("Internal server error. Please, contact support.");
        }
    }

    @Override
    public void registerCard(String accountId, String nonce, String streetAddress, String city, String state, String country)
            throws ServerException, ForbiddenException {
        if (accountId == null) {
            throw new ForbiddenException("Account ID required.");
        }
        if (nonce == null) {
            throw new ForbiddenException("Credit card nonce is required.");
        }
        Result<Customer> result;
        try {
            Customer customer = gateway.customer().find(accountId);
            if (customer.getCreditCards().size() >= 1) {
                String msg = String.format(
                        " We were unable to add your credit card, because there is already a card linked with it. If you have questions " +
                        "send an email to account-help@codenvy.com and include this ID (%s)", accountId);
                LOG.error(msg);
                throw new ForbiddenException(msg);
            }
            CustomerRequest request = new CustomerRequest().creditCard()
                                                           .paymentMethodNonce(nonce)
                                                           .billingAddress()
                                                           .streetAddress(streetAddress)
                                                           .locality(city)
                                                           .region(state)
                                                           .countryName(country)
                                                           .done()
                                                           .done();
            result = gateway.customer().update(customer.getId(), request);
            if (!result.isSuccess()) {
                String msg =
                        String.format("We were unable to add your credit card, please check card number and CVV which must be 4 digits " +
                                      "for American Express cards and 3 digits for all other card types. If problems persist send an " +
                                      "email to account-help@codenvy.com and include this ID (%s). Error Message: %s ", accountId,
                                      result.getMessage());
                LOG.error(msg);
                throw new ForbiddenException(msg);
            }
            com.braintreegateway.CreditCard card = result.getTarget().getCreditCards().get(0);
            eventService.publish(CreditCardRegistrationEvent.creditCardAddedEvent(accountId, card.getMaskedNumber(),
                                                                                  EnvironmentContext.getCurrent().getUser().getId()));
            subscriptionMailSender.sendCCAddedNotification(accountId, card.getLast4(), card.getCardType());
        } catch (NotFoundException nf) {
            CustomerRequest request = new CustomerRequest().id(accountId).creditCard()
                                                           .paymentMethodNonce(nonce)
                                                           .billingAddress()
                                                           .streetAddress(streetAddress)
                                                           .locality(city)
                                                           .region(state)
                                                           .countryName(country)
                                                           .done()
                                                           .done();
            result = gateway.customer().create(request);
            if (!result.isSuccess()) {
                String msg =
                        String.format("We were unable to add your credit card, please check card number and CVV which must be 4 digits " +
                                      "for American Express cards and 3 digits for all other card types. If problems persist send an " +
                                      "email to account-help@codenvy.com and include this ID (%s). Error Message: %s ", accountId,
                                      result.getMessage());

                LOG.error(msg);
                throw new ForbiddenException(msg);
            }
            com.braintreegateway.CreditCard card = result.getTarget().getCreditCards().get(0);
            eventService.publish(CreditCardRegistrationEvent
                                         .creditCardAddedEvent(accountId, card.getMaskedNumber(),
                                                               EnvironmentContext.getCurrent().getUser().getId()));
            subscriptionMailSender.sendCCAddedNotification(accountId, card.getLast4(), card.getCardType());
        } catch (BraintreeException e) {
            LOG.warn("Braintree exception: ", e);
            throw new ServerException("Internal server error. Please, contact support.");
        }
    }

    @Override
    public List<CreditCard> getCards(String accountId) throws ServerException, ForbiddenException {
        if (accountId == null) {
            throw new ForbiddenException("Account ID required.");
        }
        List<CreditCard> result = new ArrayList<>();
        try {
            Customer customer = gateway.customer().find(accountId);
            for (com.braintreegateway.CreditCard card : customer.getCreditCards()) {
                result.add(DtoFactory.getInstance().createDto(CreditCard.class).withAccountId(accountId)
                                     .withToken(card.getToken())
                                     .withType(card.getCardType())
                                     .withNumber(card.getMaskedNumber())
                                     .withCardholder(card.getCardholderName())
                                     .withStreetAddress(card.getBillingAddress().getStreetAddress())
                                     .withCity(card.getBillingAddress().getLocality())
                                     .withState(card.getBillingAddress().getRegion())
                                     .withCountry(card.getBillingAddress().getCountryName())
                                     .withExpiration(card.getExpirationDate()));
            }
        } catch (NotFoundException nf) {
            // nothing found - empty list
            return result;
        } catch (BraintreeException e) {
            LOG.warn("Braintree exception: ", e);
            throw new ServerException("Internal server error. Please, contact support.");
        }
        return result;
    }

    @Override
    public void deleteCard(String accountId, String token) throws ServerException, ForbiddenException {
        if (accountId == null) {
            throw new ForbiddenException("Account ID required.");
        }
        if (token == null) {
            throw new ForbiddenException("Token is required.");
        }
        try {
            com.braintreegateway.CreditCard card = gateway.creditCard().find(token);
            Result<com.braintreegateway.CreditCard> result = gateway.creditCard().delete(token);
            if (!result.isSuccess()) {
                LOG.warn(String.format("Failed to remove card. Error message: %s", result.getMessage()));
                throw new ForbiddenException(String.format(
                        "Failed to remove card. If problems persist send an email to account-help@codenvy.com and include this ID (%s). " +
                        "Error Message: %s",
                        accountId, result.getMessage()));
            }
            eventService.publish(CreditCardRegistrationEvent
                                         .creditCardRemovedEvent(accountId, card.getMaskedNumber(),
                                                                 EnvironmentContext.getCurrent().getUser().getId()));
            afterDeleteCheckAndNotify(accountId, card);
        } catch (BraintreeException e) {
            LOG.warn("Braintree exception: ", e);
            throw new ServerException("Internal server error. Please, contact support.");
        }
    }


    private void afterDeleteCheckAndNotify(String accountId, com.braintreegateway.CreditCard card) throws ServerException {
        //Get paid GbH before removing subscription
        ResourcesFilter filter = ResourcesFilter.builder().withAccountId(accountId)
                                                .withPaidGbHMoreThan(0)
                                                .withFromDate(billingPeriod.getCurrent().getStartDate().getTime())
                                                .withTillDate(System.currentTimeMillis())
                                                .build();
        List<AccountResources> resources = billingService.getEstimatedUsageByAccount(filter);

        // Remove saas subscription
        try {
            final Subscription activeSaasSubscription = accountDao.getActiveSubscription(accountId, ServiceId.SAAS);
            if (activeSaasSubscription != null && !"sas-community".equals(activeSaasSubscription.getPlanId())) {
                accountDao.updateSubscription(activeSaasSubscription.withState(SubscriptionState.INACTIVE));
                registry.get(ServiceId.SAAS).onRemoveSubscription(activeSaasSubscription);
            }
        } catch (ApiException e) {
            LOG.warn("Unable to remove subscription after CC deletion.", e);
        }

        // Check is paid resources used, lock and send notify emails.
        if (!resources.isEmpty()) {
            accountLocker.lock(accountId);
            subscriptionMailSender.sendAccountLockedNotification(accountId, Double.toString(resources.get(0).getPaidAmount()));
        } else {
            subscriptionMailSender.sendCCRemovedNotification(accountId, card.getLast4(), card.getCardType());
        }
    }
}