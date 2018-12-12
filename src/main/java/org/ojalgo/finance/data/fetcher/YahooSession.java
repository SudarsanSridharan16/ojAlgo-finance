/*
 * Copyright 1997-2018 Optimatika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.finance.data.fetcher;

import java.io.Reader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.ResourceLocator;
import org.ojalgo.netio.ResourceLocator.Request;
import org.ojalgo.netio.ResourceLocator.Response;
import org.ojalgo.netio.ResourceLocator.Session;
import org.ojalgo.type.CalendarDateDuration;
import org.ojalgo.type.CalendarDateUnit;

public class YahooSession {

    public static class Fetcher implements DataFetcher {

        private final CalendarDateUnit myResolution;
        private final ResourceLocator.Session mySession;
        private final String mySymbol;

        Fetcher(ResourceLocator.Session session, String symbol, CalendarDateUnit resolution) {
            super();
            mySession = session;
            mySymbol = symbol;
            myResolution = resolution;

        }

        public CalendarDateUnit getResolution() {
            return myResolution;
        }

        public Reader getStreamOfCSV() {

            String sessionId = mySession.getParameterValue(SESSION_ID);
            if ((sessionId == null) || (sessionId.length() <= 0)) {

                Request challengeRequest = YahooSession.buildChallengeRequest(mySession, mySymbol);
                Response challengeResponse = challengeRequest.response();

                // Must get this after the http body has been read
                if (!challengeResponse.isResponseOK() || (challengeResponse.toString() == null)) {
                    BasicLogger.error();
                    BasicLogger.error("Fetcher Problem!");
                    BasicLogger.error("Original Challenge Reguest: {}", challengeRequest);
                    BasicLogger.error("Recreated Challenge Reguest: {}", challengeResponse.getRequest());
                    BasicLogger.error("Actual Challenge Response: {}", challengeResponse);
                    BasicLogger.error();
                }

                if (YahooSession.scrapeChallengeResponse(mySession, challengeRequest, challengeResponse)) {

                    Request consentRequest = YahooSession.buildConsentRequest(mySession, challengeRequest);
                    Response consentResponse = consentRequest.response();

                    if (!consentResponse.isResponseOK()) {
                        BasicLogger.error();
                        BasicLogger.error("Fetcher Problem!");
                        BasicLogger.error("Original Consent Reguest: {}", consentRequest);
                        BasicLogger.error("Recreated Consent Reguest: {}", consentResponse.getRequest());
                        BasicLogger.error("Actual Consent Response: {}", consentResponse);
                        BasicLogger.error();
                    }
                }
            }

            String crumb = mySession.getParameterValue(CRUMB);
            if ((crumb == null) || (crumb.length() <= 0)) {

                Request crumbRequest = YahooSession.buildCrumbRequest(mySession);
                Response crumbResponse = crumbRequest.response();

                YahooSession.scrapeCrumbResponse(mySession, crumbResponse);

                if (!crumbResponse.isResponseOK() || (mySession.getParameterValue(CRUMB) == null)) {
                    BasicLogger.error();
                    BasicLogger.error("Fetcher Problem!");
                    BasicLogger.error("Original Crumb Reguest: {}", crumbRequest);
                    BasicLogger.error("Recreated Crumb Reguest: {}", crumbResponse.getRequest());
                    BasicLogger.error("Actual Crumb Response: {}", crumbResponse);
                    BasicLogger.error();
                }
            }

            ResourceLocator.Request request = YahooSession.buildDataRequest(mySession, mySymbol, myResolution);
            ResourceLocator.Response response = request.response();

            return response.getStreamReader();
        }

        public String getSymbol() {
            return mySymbol;
        }

    }

    private static final CalendarDateDuration DURATION_30_YEARS = new CalendarDateDuration(30, CalendarDateUnit.YEAR);
    private static final String END = "\">";
    private static final String FINANCE_YAHOO_COM = "finance.yahoo.com";
    private static final String GUCE_OATH_COM = "guce.oath.com";
    private static final String INPUT_TYPE_HIDDEN_NAME_BRAND_BID_VALUE = "<input type=\"hidden\" name=\"brandBid\" value=\"";
    private static final String INPUT_TYPE_HIDDEN_NAME_CSRF_TOKEN_VALUE = "<input type=\"hidden\" name=\"csrfToken\" value=\"";
    private static final String INTERVAL = "interval";
    private static final String QUERY1_FINANCE_YAHOO_COM = "query1.finance.yahoo.com";

    static final String BRAND_BID = "brandBid";
    static final String CRUMB = "crumb";
    static final String CSRF_TOKEN = "csrfToken";
    static final String SESSION_ID = "sessionId";

    /**
     * A request that requires consent, but not the crumb
     */
    static ResourceLocator.Request buildChallengeRequest(ResourceLocator.Session session, String symbol) {
        return session.request().host(FINANCE_YAHOO_COM).path("/quote/" + symbol);
    }

    static ResourceLocator.Request buildConsentRequest(ResourceLocator.Session session, ResourceLocator.Request challengeRequest) {

        String sessionID = session.getParameterValue(SESSION_ID);
        String csrfToken = session.getParameterValue(CSRF_TOKEN);
        String brandBid = session.getParameterValue(BRAND_BID);

        Request request = session.request().method(ResourceLocator.Method.POST).host(GUCE_OATH_COM).path("/consent");

        request.form("country", "SE");
        request.form("ybarNamespace", "YAHOO");
        request.form("previousStep", "");
        request.form("tosId", "eu");
        request.form("jurisdiction", "");
        request.form("originalDoneUrl", challengeRequest.toString());
        request.form(BRAND_BID, brandBid);
        request.form(SESSION_ID, sessionID);
        request.form("agree", "agree");
        request.form("locale", "sv-SE");
        request.form("isSDK", "false");
        request.form(CSRF_TOKEN, csrfToken);
        request.form("inline", "false");
        request.form("namespace", "yahoo");
        request.form("consentCollectionStep", "EU_SINGLEPAGE");
        request.form("doneUrl", "https://guce.yahoo.com/copyConsent?sessionId=" + sessionID + "&inline=false&lang=sv-SE");
        request.form("startStep", "EU_SINGLEPAGE");
        request.form("userType", "NON_REG");

        return request;
    }

    static Request buildCrumbRequest(ResourceLocator.Session session) {
        return session.request().host(QUERY1_FINANCE_YAHOO_COM).path("/v1/test/getcrumb");
    }

    static ResourceLocator.Request buildDataRequest(Session session, String symbol, CalendarDateUnit resolution) {

        ResourceLocator.Request request = session.request().host(QUERY1_FINANCE_YAHOO_COM).path("/v7/finance/download/" + symbol);

        switch (resolution) {
        case MONTH:
            request.query(INTERVAL, 1 + "mo");
            break;
        case WEEK:
            request.query(INTERVAL, 1 + "wk");
            break;
        default:
            request.query(INTERVAL, 1 + "d");
            break;
        }

        request.query("events", "history");

        final Instant now = Instant.now();
        final Instant past = now.minus(DURATION_30_YEARS.toDurationInMillis(), ChronoUnit.MILLIS);

        request.query("period1", Long.toString(past.getEpochSecond()));
        request.query("period2", Long.toString(now.getEpochSecond()));
        request.query(CRUMB, session.getParameterValue(CRUMB));

        return request;
    }

    static boolean scrapeChallengeResponse(ResourceLocator.Session session, ResourceLocator.Request challengeRequest,
            ResourceLocator.Response challengeResponse) {

        String challengeResponseBody = challengeResponse.toString();
        ResourceLocator.Request finalRequest = challengeResponse.getRequest();

        String sessionId = finalRequest.getQueryValue(SESSION_ID);
        session.parameter(SESSION_ID, sessionId);

        if (!challengeRequest.equals(challengeResponse.getRequest())) {

            int begin = challengeResponseBody.indexOf(INPUT_TYPE_HIDDEN_NAME_CSRF_TOKEN_VALUE);
            if (begin >= 0) {
                begin += INPUT_TYPE_HIDDEN_NAME_CSRF_TOKEN_VALUE.length();
            }
            int end = challengeResponseBody.indexOf(END, begin);

            String csrfToken = challengeResponseBody.substring(begin, end);
            session.parameter(CSRF_TOKEN, csrfToken);

            begin = challengeResponseBody.indexOf(INPUT_TYPE_HIDDEN_NAME_BRAND_BID_VALUE);
            if (begin >= 0) {
                begin += INPUT_TYPE_HIDDEN_NAME_BRAND_BID_VALUE.length();
            }
            end = challengeResponseBody.indexOf(END, begin);

            String brandBid = challengeResponseBody.substring(begin, end);
            session.parameter(BRAND_BID, brandBid);

            return true;

        } else {

            return false;
        }
    }

    static void scrapeCrumbResponse(ResourceLocator.Session session, ResourceLocator.Response crumbResponse) {
        session.parameter(CRUMB, crumbResponse.toString());
    }

    private final ResourceLocator.Session mySession = ResourceLocator.session();

    public YahooSession() {
        super();
    }

    public Fetcher newFetcher(String symbol, CalendarDateUnit resolution) {
        return new Fetcher(mySession, symbol, resolution);
    }

}