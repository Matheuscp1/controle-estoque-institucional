<template>
  <v-dialog :value="true" :persistent="busy" max-width="560" aria-labelledby="edit-item-title" @input="close">
    <v-card class="dialog-card">
      <form @submit.prevent="save">
        <v-card-title id="edit-item-title">Editar item {{ item.codigo }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" text role="alert">{{ error }}</v-alert>
          <v-text-field v-model="draft.descricao" label="Descrição" outlined :disabled="busy" maxlength="500" required />
          <v-text-field v-model="draft.unidade" label="Unidade de medida" outlined :disabled="busy" maxlength="30" required />
          <v-text-field v-model="draft.saldo" label="Saldo disponível" outlined :disabled="busy" inputmode="decimal" required />
          <v-switch v-model="draft.ativo" label="Item ativo" color="primary" :disabled="busy" hide-details />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn type="button" text :disabled="busy" @click="close">Fechar</v-btn>
          <v-btn v-if="needsReload" type="button" color="primary" depressed :loading="busy" @click="reload">
            Recarregar dados
          </v-btn>
          <v-btn v-else color="primary" type="submit" depressed :loading="busy">Salvar alterações</v-btn>
        </v-card-actions>
      </form>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue'
import { LosslessNumber } from 'lossless-json'
import { errorMessage, request, type Item } from '../../../services/api'
import { decimalInput } from '../../../utils/decimal'

function editFields({ descricao, unidade, saldo, ativo, versao }: Item) {
  return { descricao, unidade, saldo: String(saldo).replace('.', ','), ativo, versao }
}

export default Vue.extend({
  props: { item: { type: Object, required: true } },
  data() {
    return { draft: editFields(this.item), busy: false, needsReload: false, error: '' }
  },
  computed: {
    itemPath() {
      return `/itens/${encodeURIComponent(this.item.codigo)}`
    },
  },
  methods: {
    close() {
      if (!this.busy) this.$emit('close')
    },
    async save() {
      if (this.busy || this.needsReload) return
      const { descricao, unidade, saldo, ativo, versao } = this.draft
      const normalizedBalance = decimalInput(saldo)
      if (!descricao.trim() || !unidade.trim() || normalizedBalance === null) {
        this.error = 'Informe a descrição, a unidade e um saldo não negativo com até três casas decimais.'
        return
      }
      this.error = ''
      this.busy = true
      try {
        await request(this.itemPath, {
          method: 'PUT',
          body: {
            descricao: descricao.trim(),
            unidade: unidade.trim(),
            saldo: new LosslessNumber(normalizedBalance.replace(/^0+(?=\d)/, '')),
            ativo,
            versao: new LosslessNumber(String(versao)),
          },
        })
        this.$emit('saved')
      } catch (error) {
        this.needsReload = error.status === 409
        this.error = this.needsReload
          ? 'O item foi alterado desde a consulta. Recarregue os dados e revise os campos antes de salvar.'
          : errorMessage(error)
      } finally {
        this.busy = false
      }
    },
    async reload() {
      this.busy = true
      try {
        this.draft = editFields(await request<Item>(this.itemPath))
        this.needsReload = false
        this.error = ''
      } catch (error) {
        this.error = errorMessage(error)
      } finally {
        this.busy = false
      }
    },
  },
})
</script>
