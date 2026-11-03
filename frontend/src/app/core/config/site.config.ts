/**
 * Business details shown on the storefront. Fill in the empty values before launch;
 * anything left empty is simply not displayed (nothing is invented).
 */
export interface SiteConfig {
  brandName: string;
  /** Short wordmark shown in the header. */
  wordmark: string;
  tagline: string;
  supportEmail: string | null;
  supportPhone: string | null;
  /** Registered business address, one line per entry. */
  address: string[];
  businessHours: string | null;
  social: { label: string; url: string }[];
  /** Image per category code for the home page tiles and collection banners. */
  categoryImages: Record<string, string>;
}

export const SITE: SiteConfig = {
  brandName: 'T-Shirt Shop',
  wordmark: 'T—SHIRT SHOP',
  tagline: 'Premium everyday clothing and T-Shirts designed by you.',
  supportEmail: null, // e.g. 'support@yourdomain.in'
  supportPhone: null, // e.g. '+91 98765 43210'
  address: [], // e.g. ['Shop 12, MG Road', 'Pune, Maharashtra 411001']
  businessHours: null, // e.g. 'Mon–Sat, 10 am – 7 pm IST'
  social: [], // e.g. [{ label: 'Instagram', url: 'https://instagram.com/yourshop' }]
  categoryImages: {
    TSHIRTS: '/assets/products/essential-crew-neck-tee-black.jpg',
    SHIRTS: '/assets/products/oxford-button-down-shirt-sky_blue.jpg',
    PANTS: '/assets/products/stretch-slim-chinos-khaki.jpg',
    CUSTOM_TSHIRTS: '/assets/home/custom-tshirt.jpg',
  },
};

/** Storefront URL for a category: the custom T-Shirt category opens the designer. */
export function categoryLink(category: { code: string; slug: string }): string {
  return category.code === 'CUSTOM_TSHIRTS' ? '/customize' : `/shop/${category.slug}`;
}
