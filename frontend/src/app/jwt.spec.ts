import { decodificarJwt } from './jwt';

/**
 * El token de ejemplo es el mismo de la guia 1.2.2b: su firma es falsa y no
 * sirve para autenticar nada. Justamente por eso se puede leer aqui: decodificar
 * no es verificar.
 */
const TOKEN_DE_EJEMPLO =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.' +
  'eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFuYSBQZXJlei' +
  'IsImVtYWlsIjoiYW4ucGVyZXpAZHVvY3VjLmNsIiwiZXhwIjox' +
  'Nzg5MDAzNjAwfQ.' +
  'FIRMA_FALSA';

describe('decodificarJwt', () => {
  it('lee los claims del payload', () => {
    const claims = decodificarJwt(TOKEN_DE_EJEMPLO);

    expect(claims?.email).toBe('an.perez@duocuc.cl');
    expect(claims?.name).toBe('Ana Perez');
    expect(claims?.exp).toBe(1789003600);
  });

  it('devuelve null si no es un JWT', () => {
    expect(decodificarJwt('esto-no-es-un-jwt')).toBeNull();
    expect(decodificarJwt(null)).toBeNull();
  });
});
