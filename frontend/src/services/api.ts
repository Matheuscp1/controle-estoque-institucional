import { parse, stringify } from 'lossless-json'

export interface Item {
  codigo: string
  descricao: string
  unidade: string
  saldo: string
  ativo: boolean
  versao: string
}

export interface Organization {
  codigo: string
  sigla: string
  nome: string
}

export interface Order {
  id: string
  idExterno: string
  solicitante: string
  dataHora: string
  situacao: 'CONFIRMADO' | 'CANCELADO'
  dataCancelamento: string | null
  motivoCancelamento: string | null
  itens: Array<{ codigoItem: string; quantidade: string }>
}

export interface PageResult<T> {
  registros: T[]
  total: string
}

interface RequestOptions {
  method?: string
  params?: Record<string, string | number | null | undefined>
  body?: unknown
  signal?: AbortSignal
}

interface ErrorResponse {
  mensagem?: string
  erros?: string[]
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function request<T>(path: string, { method = 'GET', params, body, signal }: RequestOptions = {}): Promise<T> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== '' && value != null) query.set(key, String(value))
  }
  const url = query.size ? `/api${path}?${query}` : `/api${path}`
  const response = await fetch(url, {
    method,
    signal,
    headers: body ? { 'Content-Type': 'application/json', Accept: 'application/json' } : { Accept: 'application/json' },
    body: body ? stringify(body) : undefined,
  })
  const isJson = (response.headers.get('content-type') || '').includes('json')
  let data: T | ErrorResponse | null = null
  if (isJson) {
    data = parse(await response.text(), null, { parseNumber: (value) => value }) as T | ErrorResponse
  }
  if (!response.ok) {
    const errorData = data as ErrorResponse | null
    const message = errorData?.mensagem || (response.status === 503
      ? 'O serviço está temporariamente indisponível. Tente novamente em instantes.'
      : 'Não foi possível concluir a operação.')
    const details = Array.isArray(errorData?.erros) ? errorData.erros.filter((detail) => typeof detail === 'string') : []
    throw new ApiError(response.status, [message, ...details].join(' '))
  }
  return data as T
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError
    ? error.message
    : 'Não foi possível consultar o serviço. Verifique a conexão e tente novamente.'
}
