import type { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'

export function withRequestSignature(
  config: InternalAxiosRequestConfig,
  getUri: (config: AxiosRequestConfig) => string,
  signer: (input: string) => string
): InternalAxiosRequestConfig
