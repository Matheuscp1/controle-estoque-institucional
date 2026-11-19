// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import CursorList from './CursorList.vue'

const flushPromises = () => new Promise<void>((resolve) => setTimeout(resolve, 0))

function mountList(fetchPage: (...args: unknown[]) => Promise<{ registros: unknown[]; total: string }>, filters = { descricao: '' }) {
  return mount(CursorList, {
    propsData: {
      title: 'Itens', emptyMessage: 'Nenhum item', totalLabel: 'Total de itens', filters, fetchPage, cursorKey: 'codigo', limit: 2,
    },
    stubs: ['v-btn', 'v-icon', 'v-alert', 'v-progress-linear'],
  })
}

describe('lista paginada', () => {
  it('usa o último código como cursor e evita avançar após página vazia', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce({ registros: [{ codigo: 'A' }, { codigo: 'B' }], total: '2' })
      .mockResolvedValueOnce({ registros: [], total: '1' })
    const wrapper = mountList(fetchPage)
    await flushPromises()

    expect(wrapper.text()).toContain('Página 1')
    expect(wrapper.text()).toContain('Total de itens: 2')
    ;(wrapper.vm as any).advance()
    await flushPromises()

    expect(fetchPage.mock.calls[1][1]).toBe('B')
    expect((wrapper.vm as any).page).toBe(0)
    expect((wrapper.vm as any).canAdvance).toBe(false)
    expect(wrapper.text()).toContain('Total de itens: 1')
    wrapper.destroy()
  })

  it('descarta resposta antiga quando os filtros mudam', async () => {
    let completeOld!: (page: { registros: unknown[]; total: string }) => void
    const fetchPage = vi.fn()
      .mockImplementationOnce(() => new Promise((resolve) => { completeOld = resolve }))
      .mockResolvedValueOnce({ registros: [{ codigo: 'NOVO' }], total: '1' })
    const wrapper = mountList(fetchPage)
    await wrapper.setProps({ filters: { descricao: 'novo' } })
    await flushPromises()
    completeOld({ registros: [{ codigo: 'ANTIGO' }], total: '999' })
    await flushPromises()

    expect((wrapper.vm as any).rows).toEqual([{ codigo: 'NOVO' }])
    expect(wrapper.text()).toContain('Total de itens: 1')
    expect(fetchPage.mock.calls[0][3].aborted).toBe(true)
    wrapper.destroy()
  })

  it('mostra erro e permite repetir a consulta', async () => {
    const fetchPage = vi.fn()
      .mockRejectedValueOnce(new Error('falhou'))
      .mockResolvedValueOnce({ registros: [{ codigo: 'A' }], total: '1' })
    const wrapper = mountList(fetchPage)
    await flushPromises()
    expect((wrapper.vm as any).error).toBeTruthy()

    ;(wrapper.vm as any).retry()
    await flushPromises()
    expect((wrapper.vm as any).rows).toEqual([{ codigo: 'A' }])
    expect((wrapper.vm as any).error).toBe('')
    wrapper.destroy()
  })

  it('mostra o total mesmo sem registros na página', async () => {
    const wrapper = mountList(vi.fn().mockResolvedValue({ registros: [], total: '0' }))
    await flushPromises()

    expect(wrapper.text()).toContain('Total de itens: 0')
    expect(wrapper.text()).toContain('Nenhum item')
    wrapper.destroy()
  })

  it('formata o total com separador de milhar', async () => {
    const wrapper = mountList(vi.fn().mockResolvedValue({ registros: [{ codigo: 'A' }], total: '1234' }))
    await flushPromises()

    expect(wrapper.text()).toContain('Total de itens: 1.234')
    wrapper.destroy()
  })
})
