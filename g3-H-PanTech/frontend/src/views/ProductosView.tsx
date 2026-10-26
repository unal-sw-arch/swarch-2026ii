import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Product, ProductCategory, ProductInput } from '../types'

const EMPTY_FORM: ProductInput = {
  name: '',
  category: 'vitrina',
  prep_minutes: 1,
  price: 0,
  active: true,
}

export default function ProductosView() {
  const [products, setProducts] = useState<Product[]>([])
  const [form, setForm] = useState<ProductInput>({ ...EMPTY_FORM })
  const [editingId, setEditingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = async () => {
    try {
      setProducts(await api.listProducts())
      setError(null)
    } catch (err) {
      setError((err as Error).message)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const submit = async () => {
    if (!form.name.trim()) {
      setError('El nombre es obligatorio')
      return
    }
    try {
      if (editingId === null) {
        await api.createProduct(form)
        setMessage('Producto creado')
      } else {
        await api.updateProduct(editingId, form)
        setMessage('Producto actualizado')
      }
      setForm({ ...EMPTY_FORM })
      setEditingId(null)
      setError(null)
      await load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const edit = (product: Product) => {
    setEditingId(product.id)
    setForm({
      name: product.name,
      category: product.category,
      prep_minutes: product.prep_minutes,
      price: Number(product.price),
      active: product.active,
    })
  }

  const remove = async (id: number) => {
    try {
      await api.deleteProduct(id)
      await load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const toggleActive = async (product: Product) => {
    try {
      await api.updateProduct(product.id, { active: !product.active })
      await load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <div className="grid two">
      <section className="card">
        <h2>{editingId === null ? 'Nuevo producto' : `Editar producto #${editingId}`}</h2>
        {message && <p className="ok">{message}</p>}
        {error && <p className="error">{error}</p>}
        <label>
          Nombre
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </label>
        <label>
          Categoría
          <select
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value as ProductCategory })}
          >
            <option value="vitrina">Vitrina (rápido)</option>
            <option value="preparado">Preparado</option>
          </select>
        </label>
        <div className="row">
          <label>
            Minutos de preparación
            <input
              type="number"
              min={0}
              value={form.prep_minutes}
              onChange={(e) => setForm({ ...form, prep_minutes: Number(e.target.value) })}
            />
          </label>
          <label>
            Precio
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.price}
              onChange={(e) => setForm({ ...form, price: Number(e.target.value) })}
            />
          </label>
        </div>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => setForm({ ...form, active: e.target.checked })}
          />
          Activo
        </label>
        <div className="row">
          <button className="primary" onClick={submit}>
            {editingId === null ? 'Crear' : 'Guardar'}
          </button>
          {editingId !== null && (
            <button
              onClick={() => {
                setEditingId(null)
                setForm({ ...EMPTY_FORM })
              }}
            >
              Cancelar
            </button>
          )}
        </div>
      </section>

      <section className="card">
        <h2>Catálogo</h2>
        <table className="menu-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Nombre</th>
              <th>Categoría</th>
              <th>Prep.</th>
              <th>Precio</th>
              <th>Activo</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td>{product.id}</td>
                <td>{product.name}</td>
                <td>
                  <span className={`tag ${product.category}`}>{product.category}</span>
                </td>
                <td>{product.prep_minutes} min</td>
                <td>${Number(product.price).toFixed(2)}</td>
                <td>
                  <button className="link" onClick={() => toggleActive(product)}>
                    {product.active ? 'Sí' : 'No'}
                  </button>
                </td>
                <td className="actions">
                  <button onClick={() => edit(product)}>Editar</button>
                  <button className="danger" onClick={() => remove(product.id)}>
                    Eliminar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  )
}
