<template>
  <v-dialog :value="true" :persistent="busy" max-width="520" aria-labelledby="cancel-order-title" @input="close">
    <v-card class="dialog-card">
      <form @submit.prevent="cancelOrder">
        <v-card-title id="cancel-order-title">Cancelar pedido {{ order.idExterno }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" :type="alreadyCancelled ? 'info' : 'error'" text role="alert">{{ error }}</v-alert>
          <template v-if="!alreadyCancelled">
            <p>As quantidades do pedido voltarão ao saldo dos itens. O cancelamento é definitivo.</p>
            <v-textarea v-model="reason" label="Motivo do cancelamento" outlined rows="3" counter maxlength="500"
              :disabled="busy" required />
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn type="button" text :disabled="busy" @click="close">
            {{ alreadyCancelled ? 'Fechar' : 'Voltar' }}
          </v-btn>
          <v-btn v-if="!alreadyCancelled" color="error" type="submit" depressed :loading="busy">
            Confirmar cancelamento
          </v-btn>
        </v-card-actions>
      </form>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue'
import { errorMessage, request, type Order } from '../../../services/api'

export default Vue.extend({
  props: { order: { type: Object, required: true } },
  data: () => ({ reason: '', busy: false, error: '', alreadyCancelled: false }),
  computed: {
    orderPath() {
      return `/pedidos/${encodeURIComponent(this.order.id)}`
    },
  },
  methods: {
    close() {
      if (!this.busy) this.$emit('close')
    },
    async cancelOrder() {
      if (this.busy || this.alreadyCancelled) return
      const motivo = this.reason.trim()
      if (!motivo) {
        this.error = 'Informe o motivo do cancelamento.'
        return
      }
      this.error = ''
      this.busy = true
      try {
        await request(`${this.orderPath}/cancelamento`, { method: 'POST', body: { motivo } })
        this.$emit('cancelled')
      } catch (error) {
        this.error = errorMessage(error)
        if (error.status === 409) await this.checkStatus()
      } finally {
        this.busy = false
      }
    },
    async checkStatus() {
      try {
        const order = await request<Order>(this.orderPath)
        if (order.situacao === 'CANCELADO') {
          this.alreadyCancelled = true
          this.error = 'Este pedido já foi cancelado.'
          this.$emit('changed')
        }
      } catch (error) {
        this.error += ` ${errorMessage(error)}`
      }
    },
  },
})
</script>
