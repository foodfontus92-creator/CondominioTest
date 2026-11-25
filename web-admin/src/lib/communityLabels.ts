import type { CommunityType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useTenantOptional } from '../context/TenantContext';

export const COMMUNITY_TYPE_OPTIONS: { value: CommunityType; label: string }[] = [
  { value: 'CONDOMINIUM', label: 'Condominio' },
  { value: 'POBLACION', label: 'Población' },
  { value: 'VILLA', label: 'Villa' },
  { value: 'PASAJE', label: 'Pasaje' },
];

export interface CommunityLabels {
  communityType: CommunityType;
  isHorizontal: boolean;
  communityName: string;
  communityPlural: string;
  myCommunity: string;
  newCommunityBtn: string;
  ofCommunity: string;
  inCommunity: string;
  codeLabel: string;
  nameLabel: string;
  activeCheckboxLabel: string;
  gpsMissingSubject: string;
  unitsNav: string;
  unitsTitle: string;
  unitSingular: string;
  unitSingularLower: string;
  newUnitBtn: string;
  allUnitsOption: string;
  occupiedUnitsLabel: string;
  zoneLabel: string;
  residentsNav: string;
  residentsTitle: string;
  residentSingular: string;
  residentSingularLower: string;
  activateAppBtnText: string;
}

const HORIZONTAL_SHARED = {
  isHorizontal: true,
  unitsNav: 'Casas / Sitios',
  unitsTitle: 'Casas / Sitios',
  unitSingular: 'Casa / Sitio',
  unitSingularLower: 'vivienda',
  newUnitBtn: '+ Nueva casa / sitio',
  allUnitsOption: 'Todas las casas / sitios',
  occupiedUnitsLabel: 'Viviendas habitadas',
  zoneLabel: 'Calle / Sector',
  residentsNav: 'Vecinos / Residentes',
  residentsTitle: 'Vecinos / Residentes',
  residentSingular: 'Vecino',
  residentSingularLower: 'vecino',
  activateAppBtnText: 'Activar vivienda con código',
} as const;

export function communityTypeLabel(type?: CommunityType | null): string {
  switch (type) {
    case 'POBLACION':
      return 'Población';
    case 'VILLA':
      return 'Villa';
    case 'PASAJE':
      return 'Pasaje';
    case 'CONDOMINIUM':
    default:
      return 'Condominio';
  }
}

export function getCommunityLabels(type?: CommunityType | null): CommunityLabels {
  switch (type) {
    case 'POBLACION':
      return {
        communityType: 'POBLACION',
        communityName: 'Población',
        communityPlural: 'Poblaciones',
        myCommunity: 'Mi población',
        newCommunityBtn: '+ Nueva población',
        ofCommunity: 'de la población',
        inCommunity: 'en la población',
        codeLabel: 'Código de la población',
        nameLabel: 'Nombre de la población',
        activeCheckboxLabel: 'Población activa',
        gpsMissingSubject: 'Esta población',
        ...HORIZONTAL_SHARED,
      };
    case 'VILLA':
      return {
        communityType: 'VILLA',
        communityName: 'Villa',
        communityPlural: 'Villas',
        myCommunity: 'Mi villa',
        newCommunityBtn: '+ Nueva villa',
        ofCommunity: 'de la villa',
        inCommunity: 'en la villa',
        codeLabel: 'Código de la villa',
        nameLabel: 'Nombre de la villa',
        activeCheckboxLabel: 'Villa activa',
        gpsMissingSubject: 'Esta villa',
        ...HORIZONTAL_SHARED,
      };
    case 'PASAJE':
      return {
        communityType: 'PASAJE',
        communityName: 'Pasaje',
        communityPlural: 'Pasajes',
        myCommunity: 'Mi pasaje',
        newCommunityBtn: '+ Nuevo pasaje',
        ofCommunity: 'del pasaje',
        inCommunity: 'en el pasaje',
        codeLabel: 'Código del pasaje',
        nameLabel: 'Nombre del pasaje',
        activeCheckboxLabel: 'Pasaje activo',
        gpsMissingSubject: 'Este pasaje',
        ...HORIZONTAL_SHARED,
      };
    case 'CONDOMINIUM':
    default:
      return {
        communityType: 'CONDOMINIUM',
        isHorizontal: false,
        communityName: 'Condominio',
        communityPlural: 'Condominios',
        myCommunity: 'Mi condominio',
        newCommunityBtn: '+ Nuevo condominio',
        ofCommunity: 'del condominio',
        inCommunity: 'en el condominio',
        codeLabel: 'Código del condominio',
        nameLabel: 'Nombre del condominio',
        activeCheckboxLabel: 'Condominio activo',
        gpsMissingSubject: 'Este condominio',
        unitsNav: 'Unidades / Deptos',
        unitsTitle: 'Unidades / Deptos',
        unitSingular: 'Unidad',
        unitSingularLower: 'unidad',
        newUnitBtn: '+ Nueva unidad',
        allUnitsOption: 'Todas las unidades',
        occupiedUnitsLabel: 'Unidades habitadas',
        zoneLabel: 'Torre / Bloque',
        residentsNav: 'Residentes',
        residentsTitle: 'Residentes',
        residentSingular: 'Residente',
        residentSingularLower: 'residente',
        activateAppBtnText: 'Activar departamento con código',
      };
  }
}

export function formatUnitLabel(unit: {
  unitNumber: string;
  blockName?: string | null;
  towerOrBlock?: string | null;
}): string {
  const block = (unit.blockName ?? unit.towerOrBlock ?? '').trim();
  return block ? `${block} · ${unit.unitNumber}` : unit.unitNumber;
}

export function useCommunityLabels(): CommunityLabels {
  const tenant = useTenantOptional();
  const { user } = useAuth();
  const type = tenant?.condo?.communityType ?? user?.communityType ?? 'CONDOMINIUM';
  return getCommunityLabels(type);
}
