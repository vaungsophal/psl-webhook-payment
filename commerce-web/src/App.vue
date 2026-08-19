<script setup lang="ts">
import { computed, ref } from 'vue';

const commerceApiUrl = import.meta.env.VITE_COMMERCE_API_URL ?? 'http://localhost:8080';
const providerUrl = import.meta.env.VITE_MOCK_PROVIDER_URL ?? 'http://localhost:8081';

const orderId = ref(`order_${Math.floor(Math.random() * 100000)}`);
const amount = ref(29.99);
const currency = ref('USD');
const paymentId = ref('');
const status = ref('Ready');
const webhookResponse = ref('');
const webhookEvents = ref<unknown[]>([]);

const canComplete = computed(() => paymentId.value.length > 0);

async function checkCommerceHealth() {
  status.value = 'Checking Commerce API';
  const response = await fetch(`${commerceApiUrl}/api/health`);
  status.value = JSON.stringify(await response.json(), null, 2);
}

async function createPayment() {
  status.value = 'Creating mock payment';
  const response = await fetch(`${providerUrl}/api/mock-payments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      orderId: orderId.value,
      amount: amount.value,
      currency: currency.value
    })
  });
  const data = await response.json();
  paymentId.value = data.paymentId;
  status.value = JSON.stringify(data, null, 2);
}

async function completePayment() {
  if (!paymentId.value) return;
  status.value = 'Completing mock payment and sending webhook';
  const response = await fetch(`${providerUrl}/api/mock-payments/${paymentId.value}/complete`, {
    method: 'POST'
  });
  webhookResponse.value = JSON.stringify(await response.json(), null, 2);
  await loadWebhookEvents();
}

async function loadWebhookEvents() {
  const response = await fetch(`${commerceApiUrl}/api/admin/webhook-events`);
  const data = await response.json();
  webhookEvents.value = data.events ?? [];
}
</script>

<template>
  <main class="shell">
    <section class="toolbar">
      <div>
        <p class="eyebrow">Payment Webhook Learning Project</p>
        <h1>Webhook Checkout Lab</h1>
      </div>
      <button type="button" @click="checkCommerceHealth">Check API</button>
    </section>

    <section class="workspace">
      <form class="panel" @submit.prevent="createPayment">
        <h2>Start Payment</h2>
        <label>
          Order ID
          <input v-model="orderId" />
        </label>
        <label>
          Amount
          <input v-model.number="amount" min="0.01" step="0.01" type="number" />
        </label>
        <label>
          Currency
          <input v-model="currency" />
        </label>
        <button type="submit">Create Payment</button>
        <button type="button" :disabled="!canComplete" @click="completePayment">Complete Payment</button>
      </form>

      <section class="panel">
        <h2>Latest Response</h2>
        <pre>{{ status }}</pre>
        <h2>Webhook Delivery</h2>
        <pre>{{ webhookResponse || 'No webhook sent yet.' }}</pre>
      </section>
    </section>

    <section class="panel">
      <div class="events-title">
        <h2>Received Webhook Events</h2>
        <button type="button" @click="loadWebhookEvents">Refresh</button>
      </div>
      <pre>{{ JSON.stringify(webhookEvents, null, 2) }}</pre>
    </section>
  </main>
</template>
