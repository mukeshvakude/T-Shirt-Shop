import {
  activeFilterCount,
  pageWindow,
  parseListingState,
  toCatalogQuery,
  toQueryParams,
  toggle,
} from './listing-query';

describe('listing query state', () => {
  it('parses URL parameters defensively', () => {
    const s = parseListingState({
      q: '  polo ', sizes: 'm,l,M,bad code,<x>', colors: 'black', minPrice: '500', maxPrice: 'abc',
      inStock: 'true', onSale: 'yes', sort: 'price_asc', page: '3',
    });
    expect(s).toEqual({
      q: 'polo', sizes: ['M', 'L'], colors: ['BLACK'], minPrice: 500, maxPrice: null,
      inStock: true, onSale: false, sort: 'PRICE_ASC', page: 2,
    });
  });

  it('falls back to defaults for missing or invalid values', () => {
    const s = parseListingState({ sort: 'cheapest', page: '-4', minPrice: '-1' });
    expect(s.sort).toBe('NEWEST');
    expect(s.page).toBe(0);
    expect(s.minPrice).toBeNull();
    expect(s.q).toBeNull();
  });

  it('writes short URLs and round-trips', () => {
    const state = parseListingState({ sizes: 'M', sort: 'POPULAR', page: '2' });
    const params = toQueryParams(state);
    expect(params).toEqual({
      q: null, sizes: 'M', colors: null, minPrice: null, maxPrice: null, inStock: null, onSale: null,
      sort: 'POPULAR', page: 2,
    });
    expect(parseListingState(params as Record<string, string>)).toEqual(state);
    expect(toQueryParams(parseListingState({}))['page']).toBeNull();
  });

  it('builds the API query with a fixed page size', () => {
    const q = toCatalogQuery(parseListingState({ colors: 'NAVY', page: '2' }), 't-shirts');
    expect(q).toMatchObject({ category: 't-shirts', colors: ['NAVY'], page: 1, size: 12 });
  });

  it('toggles codes and counts active filters', () => {
    expect(toggle(['M'], 'L')).toEqual(['M', 'L']);
    expect(toggle(['M', 'L'], 'M')).toEqual(['L']);
    expect(activeFilterCount(parseListingState({ sizes: 'M,L', minPrice: '100', onSale: 'true' }))).toBe(4);
  });

  it('builds compact page windows', () => {
    expect(pageWindow(0, 3)).toEqual([0, 1, 2]);
    expect(pageWindow(5, 12)).toEqual([0, null, 4, 5, 6, null, 11]);
    expect(pageWindow(0, 12)).toEqual([0, 1, null, 11]);
    expect(pageWindow(11, 12)).toEqual([0, null, 10, 11]);
  });
});
