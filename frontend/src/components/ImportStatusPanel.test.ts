// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { importReadiness } from '../services/api'
import ImportStatusPanel from './ImportStatusPanel.vue'

const flushPromises = () => new Promise<void>((resolve) => setTimeout(resolve, 0))
const stubs = ['v-icon', 'v-progress-linear']

afterEach(() => {
  vi.unstubAllGlobals()
  importReadiness.ready = null
})

describe('painel da carga inicial', () => {
  it('consulta a carga ativa ao abrir e explica a ausência de job', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ImportStatusPanel, { stubs })
    await flushPromises()

    expect(fetchMock.mock.calls[0][0]).toBe('/api/imports/active')
    expect(wrapper.text()).toContain('Nenhuma carga registrada')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.destroy()
  })

  it('mostra automaticamente a etapa, os lotes e os dados publicados', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'abc', status: 'STAGING', preparedBatches: 4, completedBatches: 2,
      totalBatches: 4, publishedItems: 50000, publishedOrders: 1200, issues: 0, error: null,
    }), { headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ImportStatusPanel, { stubs })
    await flushPromises()

    expect(wrapper.text()).toContain('Processando registros')
    expect(wrapper.text()).toContain('2 de 4 lotes (50%)')
    expect(wrapper.text()).toContain('50.000')
    expect(wrapper.text()).toContain('1.200')
    wrapper.destroy()
  })

  it('confirma liberação pela API operacional após conclusão', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: 'abc', status: 'COMPLETED', preparedBatches: 4, completedBatches: 4,
        totalBatches: 4, publishedItems: 50000, publishedOrders: 1200, issues: 0, error: null,
      }), { headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response('{"registros":[],"total":0}', {
        headers: { 'Content-Type': 'application/json', 'X-Import-Ready': 'true' },
      }))
    vi.stubGlobal('fetch', fetchMock)
    importReadiness.ready = false
    const wrapper = mount(ImportStatusPanel, { stubs })
    await flushPromises()

    expect(fetchMock.mock.calls[1][0]).toContain('/api/orgaos')
    expect(importReadiness.ready).toBe(true)
    wrapper.destroy()
  })
})
