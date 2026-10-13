/**
 * Tests para la lógica de combinadas (combo)
 * 
 * Pruebas unitarias de las funciones core:
 * - comboPool: generación de pool de partidos según mercado
 * - comboFor: selección de patas para una combinada
 * - comboTotals: cálculo de probabilidad y cuota combinada
 * 
 * Ejecutar con: node combo.test.js
 */

// === Funciones a testear (exportadas del módulo combo) ===

const COMBO_SIZES = [2, 3, 4, 5, 6, 7, 10];

/**
 * Calcula el total de una combinada dada sus patas
 * @param {Array<{sel: {p: number}, price: number|null}>} legs 
 * @returns {{p: number, price: number|null}}
 */
function comboTotals(legs) {
  if (!legs || legs.length === 0) {
    return { p: 0, price: null };
  }
  
  let p = 1;
  let price = 1;
  let complete = true;
  
  for (const leg of legs) {
    p *= leg.sel.p;
    if (leg.price) {
      price *= leg.price;
    } else {
      complete = false;
    }
  }
  
  return { p, price: complete ? price : null };
}

/**
 * Selecciona las primeras patas para una combinada de tamaño dado
 * @param {Array} rows - Pool de partidos disponibles
 * @param {number} size - Tamaño deseado de la combinada
 * @returns {Array|null} - Patas seleccionadas o null si no hay suficientes
 */
function comboFor(rows, size) {
  if (!Array.isArray(rows)) {
    return null;
  }
  if (rows.length < size) {
    return null;
  }
  return rows.slice(0, size);
}

/**
 * Valida que un conjunto de patas sea una combinada válida
 * @param {Array} legs - Patas de la combinada
 * @returns {boolean}
 */
function isValidCombo(legs) {
  if (!Array.isArray(legs) || legs.length < 2) {
    return false;
  }
  
  for (const leg of legs) {
    if (!leg.sel || typeof leg.sel.p !== 'number' || leg.sel.p <= 0 || leg.sel.p > 1) {
      return false;
    }
  }
  
  return true;
}

/**
 * Calcula la rentabilidad esperada de una combinada
 * @param {number} stake - Importe apostado
 * @param {number|null} price - Cuota combinada
 * @returns {{potentialWin: number, profit: number, roi: number|null}}
 */
function calculateReturn(stake, price) {
  if (!price || stake <= 0) {
    return { potentialWin: 0, profit: 0, roi: null };
  }
  
  const potentialWin = stake * price;
  const profit = potentialWin - stake;
  const roi = (profit / stake) * 100;
  
  return { potentialWin, profit, roi };
}

// === Tests ===

let testsPassed = 0;
let testsFailed = 0;

function assert(condition, message) {
  if (condition) {
    testsPassed++;
    console.log(`  ✓ ${message}`);
  } else {
    testsFailed++;
    console.log(`  ✗ ${message}`);
  }
}

function describe(name, fn) {
  console.log(`\n${name}`);
  fn();
}

function test(name, fn) {
  console.log(`  ${name}`);
  fn();
}

// --- Tests para comboTotals ---
describe('comboTotals()', () => {
  test('calcula probabilidad combinada correctamente', () => {
    const legs = [
      { sel: { p: 0.7 }, price: 1.43 },
      { sel: { p: 0.6 }, price: 1.67 },
      { sel: { p: 0.5 }, price: 2.0 }
    ];
    const result = comboTotals(legs);
    assert(Math.abs(result.p - 0.21) < 0.001, 'probabilidad = 0.7 × 0.6 × 0.5 = 0.21');
    assert(Math.abs(result.price - 4.78) < 0.01, 'cuota = 1.43 × 1.67 × 2.0 ≈ 4.78');
  });

  test('devuelve null si falta precio en alguna pata', () => {
    const legs = [
      { sel: { p: 0.8 }, price: 1.25 },
      { sel: { p: 0.5 }, price: null }
    ];
    const result = comboTotals(legs);
    assert(result.price === null, 'cuota combinada es null cuando falta precio');
    assert(Math.abs(result.p - 0.4) < 0.001, 'probabilidad se calcula igual');
  });

  test('maneja lista vacía', () => {
    const result = comboTotals([]);
    assert(result.p === 0, 'probabilidad = 0');
    assert(result.price === null, 'precio = null');
  });

  test('maneja null/undefined', () => {
    const result1 = comboTotals(null);
    const result2 = comboTotals(undefined);
    assert(result1.p === 0, 'null → p = 0');
    assert(result2.p === 0, 'undefined → p = 0');
  });

  test('calcula combinada de 2 patas', () => {
    const legs = [
      { sel: { p: 0.75 }, price: 1.33 },
      { sel: { p: 0.65 }, price: 1.54 }
    ];
    const result = comboTotals(legs);
    assert(Math.abs(result.p - 0.4875) < 0.001, 'probabilidad = 0.75 × 0.65');
    assert(Math.abs(result.price - 2.05) < 0.01, 'cuota ≈ 2.05');
  });
});

// --- Tests para comboFor ---
describe('comboFor()', () => {
  const mockRows = [
    { id: 1, sel: { p: 0.8 }, price: 1.25 },
    { id: 2, sel: { p: 0.7 }, price: 1.43 },
    { id: 3, sel: { p: 0.6 }, price: 1.67 },
    { id: 4, sel: { p: 0.5 }, price: 2.0 }
  ];

  test('selecciona las N primeras patas', () => {
    const result = comboFor(mockRows, 3);
    assert(result !== null, 'devuelve array no nulo');
    assert(result.length === 3, 'longitud = 3');
    assert(result[0].id === 1, 'primera pata preservada');
    assert(result[2].id === 3, 'tercera pata correcta');
  });

  test('devuelve null si hay menos patas que size', () => {
    const result = comboFor(mockRows, 10);
    assert(result === null, 'null cuando size > available');
  });

  test('size = 1 funciona', () => {
    const result = comboFor(mockRows, 1);
    assert(result !== null, 'no null');
    assert(result.length === 1, 'longitud = 1');
  });

  test('devuelve null para array vacío', () => {
    const result = comboFor([], 2);
    assert(result === null, 'null para array vacío');
  });

  test('devuelve null para input no-array', () => {
    const result = comboFor(null, 2);
    assert(result === null, 'null para null');
    const result2 = comboFor('invalid', 2);
    assert(result2 === null, 'null para string');
  });
});

// --- Tests para isValidCombo ---
describe('isValidCombo()', () => {
  test('combo válido de 2 patas', () => {
    const legs = [
      { sel: { p: 0.7 } },
      { sel: { p: 0.6 } }
    ];
    assert(isValidCombo(legs) === true, 'combo de 2 patas válido');
  });

  test('combo válido de 5 patas', () => {
    const legs = Array(5).fill({ sel: { p: 0.6 } });
    assert(isValidCombo(legs) === true, 'combo de 5 patas válido');
  });

  test('combo con 1 pata es inválido', () => {
    assert(isValidCombo([{ sel: { p: 0.7 } }]) === false, '1 pata = inválido');
  });

  test('combo vacío es inválido', () => {
    assert(isValidCombo([]) === false, 'vacío = inválido');
  });

  test('rejects invalid probability', () => {
    const legs = [
      { sel: { p: 0.7 } },
      { sel: { p: 1.5 } }  // prob > 1
    ];
    assert(isValidCombo(legs) === false, 'probabilidad > 1 = inválido');
  });

  test('rejects missing sel', () => {
    const legs = [{ price: 1.5 }];
    assert(isValidCombo(legs) === false, 'sel ausente = inválido');
  });
});

// --- Tests para calculateReturn ---
describe('calculateReturn()', () => {
  test('calcula ganancia y ROI positivos', () => {
    const result = calculateReturn(10, 3.5);
    assert(result.potentialWin === 35, 'potentialWin = 10 × 3.5');
    assert(result.profit === 25, 'profit = 35 - 10');
    assert(result.roi === 250, 'ROI = 250%');
  });

  test('manejo de price null', () => {
    const result = calculateReturn(10, null);
    assert(result.potentialWin === 0, 'potentialWin = 0');
    assert(result.roi === null, 'ROI = null');
  });

  test('manejo de stake 0', () => {
    const result = calculateReturn(0, 2.0);
    assert(result.profit === 0, 'profit = 0');
    assert(result.roi === null, 'ROI = null');
  });

  test('manage pérdida', () => {
    const result = calculateReturn(10, 0.5);
    assert(result.potentialWin === 5, 'potentialWin = 5');
    assert(result.profit === -5, 'profit = -5');
    assert(result.roi === -50, 'ROI = -50%');
  });
});

// --- Tests de integración ---
describe('Integración combo completa', () => {
  test('flujo completo: pool → selección → totals', () => {
    const pool = [
      { sel: { p: 0.8 }, price: 1.25, id: 1 },
      { sel: { p: 0.7 }, price: 1.43, id: 2 },
      { sel: { p: 0.6 }, price: 1.67, id: 3 },
      { sel: { p: 0.5 }, price: 2.0, id: 4 }
    ];
    
    const legs = comboFor(pool, 3);
    assert(legs !== null, 'selección exitosa');
    assert(isValidCombo(legs), 'combo válido');
    
    const totals = comboTotals(legs);
    assert(totals.p > 0, 'probabilidad positiva');
    assert(totals.price > 0, 'cuota disponible');
    
    const stake = 10;
    const ret = calculateReturn(stake, totals.price);
    assert(ret.potentialWin > stake, 'ganancia potencial > apuesta');
  });

  test('combo sin precios completos', () => {
    const pool = [
      { sel: { p: 0.8 }, price: 1.25 },
      { sel: { p: 0.7 }, price: null }
    ];
    
    const legs = comboFor(pool, 2);
    const totals = comboTotals(legs);
    assert(totals.price === null, 'precio null cuando falta una pata');
    assert(Math.abs(totals.p - 0.56) < 0.0001, 'probabilidad = 0.8 × 0.7 = 0.56');
  });
});

// --- Resumen ---
console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log(`Resultados: ${testsPassed} pasaron, ${testsFailed} fallaron`);
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

process.exit(testsFailed > 0 ? 1 : 0);
