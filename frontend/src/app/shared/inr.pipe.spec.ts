import { InrPipe } from './inr.pipe';

describe('InrPipe', () => {
  const pipe = new InrPipe();

  it('shows whole rupees without decimals and paise with two decimals', () => {
    expect(pipe.transform(898)).toBe('₹898');
    expect(pipe.transform(1656)).toBe('₹1,656');
    expect(pipe.transform(857.2)).toBe('₹857.20');
    expect(pipe.transform(null)).toBe('');
  });
});
