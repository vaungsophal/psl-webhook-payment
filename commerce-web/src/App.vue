<script setup lang="ts">
import { computed, ref } from 'vue';

type WebhookEvent = {
  providerEventId: string;
  eventType: string;
  status: string;
  payload: string;
  receivedAt: string;
};

const commerceApiUrl = import.meta.env.VITE_COMMERCE_API_URL ?? 'http://localhost:8080';
const providerUrl = import.meta.env.VITE_MOCK_PROVIDER_URL ?? 'http://localhost:8081';

const orderId = ref(`order_${Math.floor(Math.random() * 100000)}`);
const amount = ref(29.99);
const currency = ref('USD');
const paymentId = ref('');
const orderStatus = ref('Not created');
const apiStatus = ref('Not checked');
const message = ref('Ready to create a local test payment.');
const latestResponse = ref('');
const webhookResponse = ref('');
const webhookEvents = ref<WebhookEvent[]>([]);
const isBusy = ref(false);

const canComplete = computed(() => paymentId.value.length > 0);
const canCreate = computed(() => orderId.value.trim().length > 0 && amount.value > 0 && currency.value.trim().length > 0 && !isBusy.value);
const eventCount = computed(() => webhookEvents.value.length);
const latestEvent = computed(() => webhookEvents.value[0]);
const paymentLabel = computed(() => paymentId.value || 'No payment yet');

async function checkCommerceHealth() {
  await withBusy(async () => {
    message.value = 'Checking Commerce API...';
    const response = await fetch(`${commerceApiUrl}/api/health`);
    const data = await response.json();
    apiStatus.value = response.ok ? 'Online' : 'Unavailable';
    latestResponse.value = formatJson(data);
    message.value = response.ok ? 'Commerce API is online.' : 'Commerce API did not return a healthy response.';
  });
}

function generateOrderId() {
  orderId.value = `order_${Math.floor(Math.random() * 100000)}`;
  paymentId.value = '';
  orderStatus.value = 'Not created';
  webhookResponse.value = '';
  latestResponse.value = '';
  message.value = 'New order ID is ready.';
}

async function createPayment() {
  if (!canCreate.value) return;

  await withBusy(async () => {
    paymentId.value = '';
    webhookResponse.value = '';
    orderStatus.value = 'Creating order';
    message.value = 'Creating order in Commerce API...';

    const orderResponse = await fetch(`${commerceApiUrl}/api/orders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        orderId: orderId.value,
        amount: amount.value,
        currency: currency.value
      })
    });
    const orderData = await orderResponse.json();

    if (!orderResponse.ok) {
      orderStatus.value = orderData.status ?? 'Order rejected';
      latestResponse.value = formatJson(orderData);
      message.value = orderResponse.status === 409 ? 'This order ID already exists. Generate a new one and try again.' : 'The order could not be created.';
      return;
    }

    orderStatus.value = 'Order created';
    message.value = 'Creating payment with mock provider...';

    const providerResponse = await fetch(`${providerUrl}/api/mock-payments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        orderId: orderId.value,
        amount: amount.value,
        currency: currency.value
      })
    });
    const providerData = await providerResponse.json();

    if (!providerResponse.ok) {
      latestResponse.value = formatJson(providerData);
      message.value = 'Mock provider could not create the payment.';
      return;
    }

    paymentId.value = providerData.paymentId;
    message.value = 'Registering provider payment with Commerce API...';

    const registerResponse = await fetch(`${commerceApiUrl}/api/orders/${orderId.value}/payments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        providerPaymentId: providerData.paymentId,
        amount: amount.value,
        currency: currency.value
      })
    });
    const registerData = await registerResponse.json();

    if (!registerResponse.ok) {
      orderStatus.value = registerData.status ?? 'Payment registration failed';
      latestResponse.value = formatJson(registerData);
      message.value = 'Payment registration failed in Commerce API.';
      return;
    }

    orderStatus.value = 'Payment pending';
    latestResponse.value = formatJson({
      order: orderData,
      providerPayment: providerData,
      commercePayment: registerData
    });
    message.value = 'Payment is pending. Complete it to send the signed webhook.';
  });
}

async function completePayment() {
  if (!paymentId.value) return;

  await withBusy(async () => {
    orderStatus.value = 'Completing payment';
    message.value = 'Completing mock payment and sending webhook...';
    const response = await fetch(`${providerUrl}/api/mock-payments/${paymentId.value}/complete`, {
      method: 'POST'
    });
    const data = await response.json();
    webhookResponse.value = formatJson(data);
    orderStatus.value = response.ok ? 'Webhook sent' : 'Webhook failed';
    message.value = response.ok ? 'Webhook delivered. Refreshing received events...' : 'Payment completion failed.';
    await loadWebhookEvents();
    message.value = response.ok ? 'Webhook event is visible below.' : message.value;
  });
}

async function loadWebhookEvents() {
  const response = await fetch(`${commerceApiUrl}/api/admin/webhook-events`);
  const data = await response.json();
  webhookEvents.value = data.events ?? [];
}

async function withBusy(action: () => Promise<void>) {
  isBusy.value = true;
  try {
    await action();
  } catch (error) {
    message.value = 'Request failed. Check that all containers are running.';
    latestResponse.value = error instanceof Error ? error.message : String(error);
  } finally {
    isBusy.value = false;
  }
}

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2);
}

function parsePayload(event: WebhookEvent) {
  try {
    return JSON.parse(event.payload);
  } catch {
    return null;
  }
}

function eventOrderId(event: WebhookEvent) {
  return parsePayload(event)?.data?.orderId ?? 'Unknown order';
}

function eventPaymentId(event: WebhookEvent) {
  return parsePayload(event)?.data?.paymentId ?? 'Unknown payment';
}
</script>

<template>
  <main class="shell">
    <section class="topbar">
      <div class="title-block">
        <p class="eyebrow">PSL Webhook Payment</p>
        <h1>Webhook Checkout Lab</h1>
        <p class="subtitle">{{ message }}</p>
      </div>
      <button class="secondary-button" type="button" :disabled="isBusy" @click="checkCommerceHealth">Check API</button>
    </section>

    <section class="summary-grid">
      <article class="summary-card">
        <span>Commerce API</span>
        <strong>{{ apiStatus }}</strong>
      </article>
      <article class="summary-card">
        <span>Order</span>
        <strong>{{ orderStatus }}</strong>
      </article>
      <article class="summary-card">
        <span>Payment</span>
        <strong>{{ paymentLabel }}</strong>
      </article>
      <article class="summary-card">
        <span>Webhook Events</span>
        <strong>{{ eventCount }}</strong>
      </article>
    </section>

    <section class="workspace">
      <form class="panel payment-panel" @submit.prevent="createPayment">
        <div class="panel-heading">
          <h2>Start Payment</h2>
          <span class="status-pill">{{ orderStatus }}</span>
        </div>

        <div class="field-list">
          <label>
            Order ID
            <div class="input-row">
              <input v-model="orderId" autocomplete="off" />
              <button class="secondary-button compact-button" type="button" :disabled="isBusy" @click="generateOrderId">New ID</button>
            </div>
          </label>
          <label>
            Amount
            <input v-model.number="amount" min="0.01" step="0.01" type="number" />
          </label>
          <label>
            Currency
            <input v-model="currency" autocomplete="off" />
          </label>
        </div>

        <div class="button-row">
          <button type="submit" :disabled="!canCreate">{{ isBusy ? 'Working...' : 'Create Payment' }}</button>
          <button class="secondary-button" type="button" :disabled="!canComplete || isBusy" @click="completePayment">Complete Payment</button>
        </div>
      </form>

      <section class="panel response-panel">
        <div class="panel-heading">
          <h2>Activity</h2>
        </div>
        <div class="activity-box">
          <strong>{{ orderStatus }}</strong>
          <span>{{ message }}</span>
        </div>

        <details class="details-box" :open="latestResponse.length > 0">
          <summary>Latest API response</summary>
          <pre>{{ latestResponse || 'No response yet.' }}</pre>
        </details>

        <details class="details-box" :open="webhookResponse.length > 0">
          <summary>Webhook delivery response</summary>
          <pre>{{ webhookResponse || 'No webhook sent yet.' }}</pre>
        </details>
      </section>
    </section>

    <section class="panel events-panel">
      <div class="panel-heading">
        <div>
          <h2>Received Webhook Events</h2>
          <p class="muted">{{ eventCount }} event{{ eventCount === 1 ? '' : 's' }} stored in memory</p>
        </div>
        <button class="secondary-button" type="button" :disabled="isBusy" @click="loadWebhookEvents">Refresh</button>
      </div>

      <div v-if="latestEvent" class="latest-event">
        <span>Latest event</span>
        <strong>{{ latestEvent.eventType }}</strong>
        <p>{{ eventOrderId(latestEvent) }} · {{ latestEvent.status }}</p>
      </div>

      <div v-if="webhookEvents.length" class="event-list">
        <article v-for="event in webhookEvents" :key="event.providerEventId" class="event-item">
          <div>
            <strong>{{ event.eventType }}</strong>
            <p>{{ event.providerEventId }}</p>
          </div>
          <div>
            <span class="status-pill">{{ event.status }}</span>
            <p class="event-meta">{{ eventOrderId(event) }}</p>
            <p class="event-meta">{{ eventPaymentId(event) }}</p>
          </div>
          <details class="event-details">
            <summary>Raw payload</summary>
            <pre>{{ event.payload }}</pre>
          </details>
        </article>
      </div>

      <div v-else class="empty-state">
        No webhook events received yet.
      </div>
    </section>
  </main>
</template>
