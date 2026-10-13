import { useEffect, useRef, useState } from 'react'

/** Ejecuta ``fn`` al montar y luego cada ``intervalMs`` milisegundos. */
export function usePolling<T>(fn: () => Promise<T>, intervalMs: number) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fnRef = useRef(fn)
  fnRef.current = fn

  useEffect(() => {
    let active = true
    const tick = async () => {
      try {
        const result = await fnRef.current()
        if (active) {
          setData(result)
          setError(null)
        }
      } catch (err) {
        if (active) setError((err as Error).message)
      }
    }
    tick()
    const id = window.setInterval(tick, intervalMs)
    return () => {
      active = false
      window.clearInterval(id)
    }
  }, [intervalMs])

  const refresh = async () => {
    try {
      setData(await fnRef.current())
      setError(null)
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return { data, error, refresh }
}
