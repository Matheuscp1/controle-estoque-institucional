// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CancelOrderDialog from './CancelOrderDialog.vue'

const flushPromises = () => new Promise<void>((resolve) => setTimeout(resolve, 0))
const stubs = ['v-dialog', 'v-card', 'v-card-title', 'v-card-text', 'v-card-actions', 'v-alert', 'v-textarea', 'v-spacer', 'v-btn']
const order = { id: 'pedido 1', idExterno: 'EXT-1', situacao: 'CONFIRMADO' }

afterEach(() => vi.unstubAllGlobals())

describe('cancelamento de pedido', () => {
  it('exige motivo e envia apenas o motivo sem espaços nas pontas', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(CancelOrderDialog, { propsData: { order }, stubs })
    const vm = wrapper.vm as any

    await vm.cancelOrder()
    expect(fetchMock).not.toHaveBeenCalled()
    vm.reason = '  pedido duplicado  '
    await vm.cancelOrder()

    expect(fetchMock).toHaveBeenCalledWith('/api/pedidos/pedido%201/cancelamento', expect.objectContaining({ method: 'POST', body: '{"motivo":"pedido duplicado"}' }))
    expect(wrapper.emitted('cancelled')).toHaveLength(1)
    wrapper.destroy()
  })

  it('consulta o pedido após conflito e informa quando já foi cancelado', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('{"mensagem":"Conflito"}', { status: 409, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response('{"situacao":"CANCELADO"}', { headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(CancelOrderDialog, { propsData: { order }, stubs })
    const vm = wrapper.vm as any
    vm.reason = 'duplicado'

    await vm.cancelOrder()
    await flushPromises()
    expect(vm.alreadyCancelled).toBe(true)
    expect(wrapper.emitted('changed')).toHaveLength(1)
    expect(wrapper.emitted('cancelled')).toBeUndefined()
    wrapper.destroy()
  })
})
