@US22 @EP09 @erp-integration-hub
# User Story: US22 - Audit external integrations, recover failed queues (DLQ/Outbox), and monitor operational health
Feature: Integration Audit, Dead Letter Queue Recovery, and Service Health Probes
  As a SuperAdmin or platform administrator
  I want to audit external integration attempts, inspect and reactivate DLQ messages, and check health probes
  So that transient third-party outages never cause lost transactions

  Background:
    Given the "erp-integration-hub" REST API is available

  @US22 @happy-path
  # User Story: US22 - Query integration attempts, DLQ entries, and reactivate a failed message
  Scenario: SuperAdmin audits integration attempts and reactivates a failed DLQ message
    Given an authenticated user with role "SUPER_ADMIN"
    And a failed integration event "dlq-msg-01" residing in the Dead Letter Queue
    When the client sends a "GET" request to "/api/v1/integrations/admin/attempts"
    Then the response status code should be 200
    When the client sends a "GET" request to "/api/v1/integrations/admin/dlq"
    Then the response status code should be 200
    When the client sends a "POST" request to "/api/v1/integrations/admin/dlq/dlq-msg-01/reactivate"
    Then the response status code should be 200
    And the JSON response field "status" should equal "RETRY_SCHEDULED"

  @US22 @happy-path
  # User Story: US22 - Verify public liveness health probe
  Scenario: Health probe endpoint responds with UP status
    When the client sends a "GET" request to "/health"
    Then the response status code should be 200

  @US22 @negative
  # User Story: US22 - Reject DLQ reactivation for non-existent message ID
  Scenario: Reactivating an unknown DLQ message ID returns HTTP 404 Not Found
    Given an authenticated user with role "SUPER_ADMIN"
    When the client sends a "POST" request to "/api/v1/integrations/admin/dlq/00000000-0000-0000-0000-000000009999/reactivate"
    Then the response status code should be 404
    And the JSON response should contain an error message indicating DLQ message not found
