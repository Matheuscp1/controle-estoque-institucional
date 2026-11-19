import { afterEach, describe, expect, it, vi } from 'vitest'
import { LosslessNumber } from 'lossless-json'
import { ApiError, importReadiness, request } from './api'

afterEach(() => {
  vi.unstubAllGlobals()
  importReadiness.ready = null
})

describe('cliente da API', () => {
  it('codifica filtros, ignora vazios e envia o sinal de cancelamento', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal

    await request('/itens', { params: { descricao: 'Papel A4', saldoMin: '0.001', depois: '' }, signal })

    expect(fetchMock).toHaveBeenCalledWith('/api/itens?descricao=Papel+A4&saldoMin=0.001', expect.objectContaining({ signal }))
  })

  it('preserva números grandes e casas decimais ao ler e escrever JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"versao":9007199254740993,"saldo":0.001}', {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await request<{ versao: string; saldo: string }>('/itens/A', { method: 'PUT', body: { versao: new LosslessNumber('9007199254740993') } })

    expect(result).toEqual({ versao: '9007199254740993', saldo: '0.001' })
    expect(fetchMock.mock.calls[0][1].body).toBe('{"versao":9007199254740993}')
  })

  it('inclui mensagem e detalhes da API nos erros de conflito', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"mensagem":"Conflito","erros":["Versão desatualizada"]}', {
      status: 409, headers: { 'Content-Type': 'application/json' },
    })))

    await expect(request('/itens/A')).rejects.toMatchObject({
      status: 409,
      message: 'Conflito Versão desatualizada',
    } satisfies Partial<ApiError>)
  })

  it('trata resposta sem JSON e indisponibilidade temporária', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))
    await expect(request('/itens')).rejects.toMatchObject({ status: 503 })
  })

  it('atualiza a disponibilidade de escrita pelo cabeçalho da API', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('[]', { headers: { 'Content-Type': 'application/json', 'X-Import-Ready': 'false' } }))
      .mockResolvedValueOnce(new Response('[]', { headers: { 'Content-Type': 'application/json', 'X-Import-Ready': 'true' } }))
    vi.stubGlobal('fetch', fetchMock)

    await request('/itens')
    expect(importReadiness.ready).toBe(false)
    await request('/itens')
    expect(importReadiness.ready).toBe(true)
  })
})
