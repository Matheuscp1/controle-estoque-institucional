import { parse, stringify } from 'lossless-json'

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export async function request(path, { method = 'GET', params, body, signal } = {}) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== '' && value != null) query.set(key, value)
  }
  const url = query.size ? `/api${path}?${query}` : `/api${path}`
  const response = await fetch(url, {
    method,
    signal,
    headers: body ? { 'Content-Type': 'application/json', Accept: 'application/json' } : { Accept: 'application/json' },
    body: body ? stringify(body) : undefined,
  })
  const isJson = (response.headers.get('content-type') || '').includes('json')
  let data = null
  if (isJson) {
    data = parse(await response.text(), null, { parseNumber: (value) => value })
  }
  if (!response.ok) {
    const message = data?.mensagem || (response.status === 503
      ? 'O serviço está temporariamente indisponível. Tente novamente em instantes.'
      : 'Não foi possível concluir a operação.')
    const details = Array.isArray(data?.erros) ? data.erros.filter((detail) => typeof detail === 'string') : []
    throw new ApiError(response.status, [message, ...details].join(' '))
  }
  return data
}

export function errorMessage(error) {
  return error instanceof ApiError
    ? error.message
    : 'Não foi possível consultar o serviço. Verifique a conexão e tente novamente.'
}
